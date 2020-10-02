package com.example.imagedownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.Exception

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    //ImageLoader instance
    private lateinit var imageLoader: ImageLoader

    //Permission Request Handler
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //setting up Activity Permission Request Handle
        setPermissionCallback()

        progressbar.visible(false)

        //getting imageloader instance
        imageLoader = Coil.imageLoader(this)

        button_paste_link.setOnClickListener {
            pasteLink()
        }

        button_download.setOnClickListener {
            val bitmapURL = edit_text_image_url.text.toString().trim()

            //when download is pressed check permission and save bitmap from url
            checkPermissionAndDownloadBitmap(bitmapURL)
        }

        edit_text_image_url.addTextChangedListener {
            button_download.enable(it.toString().isNotEmpty())
        }
    } //close on create function

    //Allowing activity to automatically handle permission request
    private fun setPermissionCallback() {
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                getBitmapFromUrl(edit_text_image_url.text.toString().trim())
            }
        }
    }

    //function to check and request storage permission
    private fun checkPermissionAndDownloadBitmap(bitmapURL: String) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                getBitmapFromUrl(bitmapURL)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                showPermissionRequestDialog(
                    getString(R.string.permission_title),
                    getString(R.string.write_permission_request)
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    //this function will fetch the Bitmap from the given URL
    private fun getBitmapFromUrl(bitmapURL: String) = lifecycleScope.launch {
        progressbar.visible(true)
        image_view.load(bitmapURL)
        val request = ImageRequest.Builder(this@MainActivity)
            .data(bitmapURL)
            .build()
        try {
            val downloadedBitmap = (imageLoader.execute(request).drawable as BitmapDrawable).bitmap
            image_view.setImageBitmap(downloadedBitmap)
            saveMediaToStorage(downloadedBitmap)
        } catch (e: Exception) {
            toast(e.message)
        }
        progressbar.visible(false)
    }

    //the function, is used to save the Bitmap to external storage
    private fun saveMediaToStorage(bitmap: Bitmap) {
        //generating a file name
        val filename = "${System.currentTimeMillis()}.jpg"

        //output stream
        var fos: OutputStream? = null

        //for device running android >= Q
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //getting the contentResolver
            //context?.contentResolver?.also { resolver ->
            contentResolver?.also { resolver ->

                //content resolver will process the contentvalues
                val contentValues = ContentValues().apply {
                    //putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                //inserting the contentValues to contentResolver and getting the Uri
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                //opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            //these for devices running an android <Q
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }
        fos?.use {
            //finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            //context?.toast("Saved to Photos")
            toast("Saved to Photos")
        }
    }

    //Pasting the value from Clipboard to EditText
    private fun pasteLink() {
        val clipboard: ClipboardManager? =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboard?.hasPrimaryClip() == true) {
            edit_text_image_url.setText(clipboard.primaryClip?.getItemAt(0)?.text.toString().trim())
        }
    }
}