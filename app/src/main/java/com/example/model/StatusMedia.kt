package com.example.model

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import java.io.File

data class StatusMedia(
    val uri: Uri,
    val file: File? = null,
    val name: String,
    val path: String,
    val size: Long = 0L,
    val dateModified: Long = 0L,
    val isVideo: Boolean = false,
    var isSaved: Boolean = false,
    var isSelected: Boolean = false
) : Parcelable {

    val extension: String
        get() = name.substringAfterLast('.', "")

    constructor(parcel: Parcel) : this(
        uri = parcel.readParcelable(Uri::class.java.classLoader) ?: Uri.EMPTY,
        file = parcel.readString()?.let { File(it) },
        name = parcel.readString() ?: "",
        path = parcel.readString() ?: "",
        size = parcel.readLong(),
        dateModified = parcel.readLong(),
        isVideo = parcel.readByte() != 0.toByte(),
        isSaved = parcel.readByte() != 0.toByte(),
        isSelected = parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(uri, flags)
        parcel.writeString(file?.absolutePath)
        parcel.writeString(name)
        parcel.writeString(path)
        parcel.writeLong(size)
        parcel.writeLong(dateModified)
        parcel.writeByte(if (isVideo) 1 else 0)
        parcel.writeByte(if (isSaved) 1 else 0)
        parcel.writeByte(if (isSelected) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<StatusMedia> {
        override fun createFromParcel(parcel: Parcel): StatusMedia {
            return StatusMedia(parcel)
        }

        override fun newArray(size: Int): Array<StatusMedia?> {
            return arrayOfNulls(size)
        }
    }
}
