package com.proyek.tugasproyek

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils

object CloudinaryHelper {
    lateinit var cloudinary: Cloudinary

    fun init(context: android.content.Context) {
        cloudinary = Cloudinary(
            ObjectUtils.asMap(
                "cloud_name", "drguhqtut",
                "api_key", "525777488888873",
                "api_secret", "ZbVBIPQIvoGa6-IUCnrIb0C9Ie0"
            )
        )
    }
}
