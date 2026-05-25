package com.dualstream.ui.theme

import androidx.compose.ui.graphics.Color

// ── iOS True-Black Dark Mode Palette ──────────────────────────────────────────

// System Backgrounds
val iOSBlack       = Color(0xFF000000) // Primary screen background
val iOSGrayBg      = Color(0xFF1C1C1E) // Grouped table background (cards)
val iOSLightGrayBg = Color(0xFF2C2C2E) // In-card rows / elevated surfaces
val iOSSeparator   = Color(0xFF38383A) // List dividers / thin separators

// System Tints
val iOSBlue        = Color(0xFF0A84FF) // Primary action / links
val iOSGreen       = Color(0xFF30D158) // Active / connected / success
val iOSRed         = Color(0xFFFF453A) // Stop / error / destructive
val iOSOrange      = Color(0xFFFF9F0A) // Warning / connecting state
val iOSPurple      = Color(0xFFBF5AF2) // Accent / discovery state

// Label Colors (iOS label hierarchy)
val iOSWhite       = Color(0xFFFFFFFF) // Primary label
val iOSSecondary   = Color(0xFF8E8E93) // Secondary label
val iOSTertiary    = Color(0xFF48484A) // Tertiary label / placeholder

// ── Legacy Aliases (kept for components not yet migrated) ─────────────────────
val DarkBackground  = iOSBlack
val SurfaceColor    = iOSGrayBg
val CardBackground  = iOSGrayBg
val OnSurfaceColor  = iOSWhite
val GreyText        = iOSSecondary
val LightGrey       = iOSSecondary
val ElectricBlue    = iOSBlue
val PurpleAccent    = iOSPurple
val SuccessGreen    = iOSGreen
val WarningAmber    = iOSOrange
val ErrorRed        = iOSRed
