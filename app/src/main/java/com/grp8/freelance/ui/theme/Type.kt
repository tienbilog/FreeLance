package com.grp8.freelance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.grp8.freelance.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val InterFamily = FontFamily(
    Font(GoogleFont("Inter"), provider, FontWeight.Normal),
    Font(GoogleFont("Inter"), provider, FontWeight.Medium),
    Font(GoogleFont("Inter"), provider, FontWeight.Bold)
)

val PlayfairFamily = FontFamily(
    Font(GoogleFont("Playfair Display"), provider, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge  = TextStyle(fontFamily = PlayfairFamily, fontWeight = FontWeight.Bold,   fontSize = 28.sp),
    titleLarge    = TextStyle(fontFamily = PlayfairFamily, fontWeight = FontWeight.Bold,   fontSize = 22.sp),
    titleMedium   = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge     = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium    = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall     = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelMedium   = TextStyle(fontFamily = InterFamily,    fontWeight = FontWeight.Medium, fontSize = 12.sp),
)