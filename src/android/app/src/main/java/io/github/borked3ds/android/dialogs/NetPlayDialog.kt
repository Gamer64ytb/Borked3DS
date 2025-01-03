// Copyright 2024 Mandarine Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package io.github.borked3ds.android.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.borked3ds.android.Borked3DSApplication
import io.github.borked3ds.android.R
import io.github.borked3ds.android.databinding.*
import io.github.borked3ds.android.utils.CompatUtils
import io.github.borked3ds.android.utils.NetPlayManager

class NetPlayDialog(context: Context) : BaseSheetDialog(context) {
    private lateinit var adapter: NetPlayAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (NetPlayManager.netPlayIsJoined()) {
            val binding = DialogMultiplayerBinding.inflate(layoutInflater)
            setContentView(binding.root)

            adapter = NetPlayAdapter()
            binding.listMultiplayer.layoutManager = LinearLayoutManager(context)
            binding.listMultiplayer.adapter = adapter
            adapter.loadMultiplayerMenu()

            binding.btnLeave.setOnClickListener {
                NetPlayManager.clearChat()
                NetPlayManager.netPlayLeaveRoom()
                dismiss()
            }

            binding.btnChat.setOnClickListener {
                val chatDialog = ChatDialog(context)
                chatDialog.show()
            }
        } else {
            val binding = DialogMultiplayerInitialBinding.inflate(layoutInflater)
            setContentView(binding.root)

            binding.btnCreate.setOnClickListener {
                showNetPlayInputDialog(true)
                dismiss()
            }

            binding.btnJoin.setOnClickListener {
                showNetPlayInputDialog(false)
                dismiss()
            }
        }
    }

    data class NetPlayItems(
        val option: Int,
        val name: String,
        val type: Int
    ) {
        companion object {
            // multiplayer
            const val MULTIPLAYER_ROOM_TEXT = 0
            const val MULTIPLAYER_CREATE_ROOM = 1
            const val MULTIPLAYER_JOIN_ROOM = 2
            const val MULTIPLAYER_ROOM_MEMBER = 3
            const val MULTIPLAYER_EXIT_ROOM = 4
            const val MULTIPLAYER_SEPARATOR = 5
            const val MULTIPLAYER_ROOM_COUNT = 6

            // view type
            const val TYPE_BUTTON = 0
            const val TYPE_TEXT = 1
            const val TYPE_SEPARATOR = 2
        }
    }

    abstract class NetPlayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }

        abstract fun bind(item: NetPlayItems)
        abstract override fun onClick(clicked: View)
    }

    inner class TextNetPlayViewHolder(val binding: ItemTextNetplayBinding) :
        NetPlayViewHolder(binding.root) {
        private lateinit var netPlayItem: NetPlayItems

        override fun onClick(clicked: View) {
            when (netPlayItem.option) {
                NetPlayItems.MULTIPLAYER_CREATE_ROOM -> {
                    showNetPlayInputDialog(true)
                    dismiss()
                }

                NetPlayItems.MULTIPLAYER_JOIN_ROOM -> {
                    showNetPlayInputDialog(false)
                    dismiss()
                }

                NetPlayItems.MULTIPLAYER_EXIT_ROOM -> {
                    NetPlayManager.netPlayLeaveRoom()
                    dismiss()
                }
            }
        }

        override fun bind(item: NetPlayItems) {
            netPlayItem = item
            binding.itemTextNetplayName.text = item.name
            binding.itemIcon.apply {
                val iconRes = when (item.option) {
                    NetPlayItems.MULTIPLAYER_ROOM_TEXT -> R.drawable.ic_system
                    NetPlayItems.MULTIPLAYER_ROOM_COUNT -> R.drawable.ic_joined
                    else -> 0
                }
                visibility = if (iconRes != 0) {
                    setImageResource(iconRes)
                    View.VISIBLE
                } else View.GONE
            }
        }
    }

    inner class ButtonNetPlayViewHolder(val binding: ItemButtonNetplayBinding) :
        NetPlayViewHolder(binding.root) {
        private lateinit var netPlayItems: NetPlayItems

        init {
            itemView.setOnClickListener(null)
            binding.itemButtonNetplay.apply {
                setText(R.string.multiplayer_kick_member)
                visibility = if (NetPlayManager.netPlayIsModerator()) View.VISIBLE else View.GONE
            }

            binding.itemButtonMore.apply {
                visibility = View.VISIBLE
                setOnClickListener { showPopupMenu(it) }
            }
        }

        private fun showPopupMenu(view: View) {
            PopupMenu(view.context, view).apply {
                inflate(R.menu.menu_netplay_member)
                menu.findItem(R.id.action_kick).isEnabled = NetPlayManager.netPlayIsModerator()
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_kick -> {
                            NetPlayManager.netPlayKickUser(netPlayItems.name)
                            true
                        }

                        else -> false
                    }
                }
                show()
            }
        }

        override fun bind(item: NetPlayItems) {
            netPlayItems = item
            binding.itemButtonNetplayName.text = netPlayItems.name
            binding.itemButtonNetplay.setOnClickListener { onClick(it) }
        }

        override fun onClick(clicked: View) {
            if (netPlayItems.option == NetPlayItems.MULTIPLAYER_ROOM_MEMBER && NetPlayManager.netPlayIsModerator()) {
                NetPlayManager.netPlayKickUser(netPlayItems.name)
            }
        }
    }

    inner class NetPlayAdapter : RecyclerView.Adapter<NetPlayViewHolder>() {
        private val netPlayItems = mutableListOf<NetPlayItems>()

        fun loadMultiplayerMenu() {
            val infos = NetPlayManager.netPlayRoomInfo()

            if (infos.isNotEmpty()) {
                val roomInfo = infos[0].split("|")
                val roomName = roomInfo[0]
                val maxPlayers = roomInfo[1].toInt()

                netPlayItems.add(
                    NetPlayItems(
                        NetPlayItems.MULTIPLAYER_ROOM_TEXT,
                        roomName,
                        NetPlayItems.TYPE_TEXT
                    )
                )

                netPlayItems.add(
                    NetPlayItems(
                        NetPlayItems.MULTIPLAYER_ROOM_COUNT,
                        "${infos.size - 1}/$maxPlayers", NetPlayItems.TYPE_TEXT
                    )
                )

                netPlayItems.add(
                    NetPlayItems(
                        NetPlayItems.MULTIPLAYER_SEPARATOR,
                        "",
                        NetPlayItems.TYPE_SEPARATOR
                    )
                )

                for (i in 1 until infos.size) {
                    netPlayItems.add(
                        NetPlayItems(
                            NetPlayItems.MULTIPLAYER_ROOM_MEMBER,
                            infos[i], NetPlayItems.TYPE_BUTTON
                        )
                    )
                }
            }
        }

        fun refresh() {
            netPlayItems.clear()
            loadMultiplayerMenu()
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return netPlayItems[position].type
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetPlayViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                NetPlayItems.TYPE_TEXT -> TextNetPlayViewHolder(
                    ItemTextNetplayBinding.inflate(inflater, parent, false)
                )

                NetPlayItems.TYPE_BUTTON -> ButtonNetPlayViewHolder(
                    ItemButtonNetplayBinding.inflate(inflater, parent, false)
                )

                NetPlayItems.TYPE_SEPARATOR -> object : NetPlayViewHolder(
                    inflater.inflate(R.layout.item_separator_netplay, parent, false)
                ) {
                    override fun bind(item: NetPlayItems) {}
                    override fun onClick(clicked: View) {}
                }

                else -> throw IllegalStateException("Unsupported view type")
            }
        }

        override fun onBindViewHolder(holder: NetPlayViewHolder, position: Int) {
            holder.bind(netPlayItems[position])
        }

        override fun getItemCount(): Int {
            return netPlayItems.size
        }
    }

    fun showNetPlayInputDialog(isCreateRoom: Boolean) {
        val activity = CompatUtils.findActivity(context)
        val dialog = BaseSheetDialog(activity)
        val binding = DialogMultiplayerRoomBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        binding.textTitle.text = activity.getString(
            if (isCreateRoom) R.string.multiplayer_create_room
            else R.string.multiplayer_join_room
        )

        binding.ipInfoContainer.visibility = if (isCreateRoom) View.VISIBLE else View.GONE
        binding.serverAddressContainer.visibility = if (isCreateRoom) View.GONE else View.VISIBLE

        if (isCreateRoom) {
            binding.ipAddressLabel.text = NetPlayManager.getIpAddressByWifi(activity)
        } else {
            binding.serverAddress.setText(NetPlayManager.getRoomAddress(activity))
        }

        binding.copyIpButton.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("IP Address", binding.ipAddressLabel.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(activity, R.string.multiplayer_ip_copied, Toast.LENGTH_SHORT).show()
        }

        binding.ipPort.setText(NetPlayManager.getRoomPort(activity))
        binding.username.setText(NetPlayManager.getUsername(activity))

        binding.roomName.visibility = if (isCreateRoom) View.VISIBLE else View.GONE

        binding.maxPlayersContainer.visibility = if (isCreateRoom) View.VISIBLE else View.GONE

        binding.maxPlayersLabel.text = context.getString(
            R.string.multiplayer_max_players_value,
            binding.maxPlayers.value.toInt()
        )

        binding.maxPlayers.addOnChangeListener { _, value, _ ->
            binding.maxPlayersLabel.text =
                context.getString(R.string.multiplayer_max_players_value, value.toInt())
        }

        binding.btnConfirm.setOnClickListener {
            binding.btnConfirm.isEnabled = false
            binding.btnConfirm.text = activity.getString(R.string.disabled_button_text)

            val ipAddress = if (isCreateRoom) binding.ipAddressLabel.text.toString()
            else binding.serverAddress.text.toString()
            val username = binding.username.text.toString()
            val portStr = binding.ipPort.text.toString()
            val password = binding.password.text.toString()
            val port = try {
                portStr.toInt()
            } catch (e: Exception) {
                Toast.makeText(activity, R.string.multiplayer_port_invalid, Toast.LENGTH_LONG)
                    .show()
                binding.btnConfirm.isEnabled = true
                binding.btnConfirm.text = activity.getString(R.string.original_button_text)
                return@setOnClickListener
            }
            val roomName = binding.roomName.text.toString()
            val maxPlayers = binding.maxPlayers.value.toInt()
            if (isCreateRoom && (roomName.length < 3 || roomName.length > 20)) {
                Toast.makeText(activity, R.string.multiplayer_room_name_invalid, Toast.LENGTH_LONG)
                    .show()
                binding.btnConfirm.isEnabled = true
                binding.btnConfirm.text = activity.getString(R.string.original_button_text)
                return@setOnClickListener
            }

            if (ipAddress.length < 7 || username.length < 5) {
                Toast.makeText(activity, R.string.multiplayer_input_invalid, Toast.LENGTH_LONG)
                    .show()
                binding.btnConfirm.isEnabled = true
                binding.btnConfirm.text = activity.getString(R.string.original_button_text)
            } else {
                Handler(Looper.getMainLooper()).post {
                    val operation: (String, Int, String, String, String, Int) -> Int =
                        if (isCreateRoom) {
                            { ip, port, username, pass, name, max ->
                                NetPlayManager.netPlayCreateRoom(
                                    ip,
                                    port,
                                    username,
                                    pass,
                                    name,
                                    max
                                )
                            }
                        } else {
                            { ip, port, username, pass, _, _ ->
                                NetPlayManager.netPlayJoinRoom(ip, port, username, pass)
                            }
                        }

                    val result =
                        operation(ipAddress, port, username, password, roomName, maxPlayers)
                    if (result == 0) {
                        if (isCreateRoom) {
                            NetPlayManager.setUsername(activity, username)
                            NetPlayManager.setRoomPort(activity, portStr)
                        } else {
                            NetPlayManager.setRoomAddress(activity, ipAddress)
                            NetPlayManager.setUsername(activity, username)
                            NetPlayManager.setRoomPort(activity, portStr)
                        }
                        Toast.makeText(
                            Borked3DSApplication.appContext,
                            if (isCreateRoom) R.string.multiplayer_create_room_success
                            else R.string.multiplayer_join_room_success,
                            Toast.LENGTH_LONG
                        ).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(
                            activity,
                            R.string.multiplayer_could_not_connect,
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnConfirm.isEnabled = true
                        binding.btnConfirm.text = activity.getString(R.string.original_button_text)
                    }
                }
            }
        }

        dialog.show()
    }
}
