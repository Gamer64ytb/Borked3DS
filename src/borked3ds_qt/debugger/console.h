// Copyright 2018 Citra Emulator Project
// Copyright 2024 Borked3DS Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

namespace Debugger {

/**
 * Uses the WINAPI to hide or show the stderr console. This function is a placeholder until we can
 * get a real qt logging window which would work for all platforms.
 */
void ToggleConsole();
} // namespace Debugger
