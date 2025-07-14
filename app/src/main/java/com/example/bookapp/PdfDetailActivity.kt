package com.example.bookapp

import android.Manifest
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bookapp.databinding.ActivityPdfDetailBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.io.IOException
import java.io.OutputStream

class PdfDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfDetailBinding
    private lateinit var progressDialog: ProgressDialog

    private companion object {
        const val TAG = "BOOK_DETAIL_TAG"
    }

    private var bookId = ""
    private var bookTitle = ""
    private var bookUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getStringExtra("bookId")!!

        progressDialog = ProgressDialog(this).apply {
            setTitle("Please wait...")
            setCanceledOnTouchOutside(false)
        }

        MyApplication.incrementBookViewsCount(bookId)
        loadBookDetails()

        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        binding.readBookBtn.setOnClickListener {
            val intent = Intent(this, PdfViewActivity::class.java).apply {
                putExtra("bookId", bookId)
            }
            startActivity(intent)
        }

        binding.downloadBookBtn.setOnClickListener {
            downloadBook()
        }
    }

    // Menghapus izin yang tidak diperlukan lagi.
    // Sekarang, kita tidak lagi membutuhkan izin, karena kita akan menggunakan MediaStore API.
    /* private val requestStoragePermissionLauncher =
         registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
             if (isGranted) {
                 Log.d(TAG, "onCreate: STORAGE PERMISSION is granted")
                 downloadBook()
             } else {
                 Log.d(TAG, "onCreate: STORAGE PERMISSION is denied")
                 Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
             }
         }*/

    private fun downloadBook() {
        progressDialog.setMessage("Downloading Book")
        progressDialog.show()

        val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(bookUrl)
        storageReference.getBytes(Constants.MAX_BYTES_PDF)
            .addOnSuccessListener { bytes ->
                Log.d(TAG, "downloadBook: book downloaded...")
                saveToDownloadsFolder(bytes)
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Log.d(TAG, "downloadBook: Failed to download book due to ${e.message}")
                Toast.makeText(this, "Failed to download book due to ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveToDownloadsFolder(bytes: ByteArray) {
        Log.d(TAG, "saveToDownloadsFolder: Saving downloaded book")

        val nameWithExtension = "$bookTitle.pdf"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nameWithExtension)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MyApp") // Mengatur folder tujuan di MediaStore
            }
        }

        val resolver = contentResolver
        var stream: OutputStream? = null
        var fileUri: Uri? = null

        try {
            val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            fileUri = resolver.insert(contentUri, contentValues)
            if (fileUri == null) {
                throw IOException("Failed to create new MediaStore record.")
            }
            stream = resolver.openOutputStream(fileUri)
            if (stream == null) {
                throw IOException("Failed to get output stream.")
            }
            stream.write(bytes)
            Toast.makeText(this, "Saved to Downloads Folder", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "saveToDownloadsFolder: Saved to Downloads Folder")
            incrementDownloadsCount()
        } catch (e: IOException) {
            if (fileUri != null) {
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(fileUri, null, null)
            }
            Log.e(TAG, "saveToDownloadsFolder: Failed to save due to ${e.message}")
            Toast.makeText(this, "Failed to save due to ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            stream?.close()
            progressDialog.dismiss()
        }
    }

    private fun incrementDownloadsCount() {
        Log.d(TAG, "incrementDownloadsCount")

        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var downloadsCount = "${snapshot.child("downloadsCount").value}"
                    Log.d(TAG, "onDataChange: downloadsCount: $downloadsCount")

                    if (downloadsCount == "" || downloadsCount == "null") {
                        downloadsCount = "0"
                    }

                    val newDownloadsCount: Long = downloadsCount.toLong() + 1
                    Log.d(TAG, "onDataChange: New Downloads Count: $newDownloadsCount")

                    val hashMap = hashMapOf<String, Any>("downloadsCount" to newDownloadsCount)

                    val dbRef = FirebaseDatabase.getInstance().getReference("Books")
                    dbRef.child(bookId)
                        .updateChildren(hashMap)
                        .addOnSuccessListener {
                            Log.d(TAG, "onDataChange: Downloads Count Updated incremented")
                        }
                        .addOnFailureListener { e ->
                            Log.d(TAG, "onDataChange: FAILED to increment due to ${e.message}")
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.d(TAG, "onCancelled: ${error.message}")
                }
            })
    }

    private fun loadBookDetails() {
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val categoryId = "${snapshot.child("categoryId").value}"
                    val description = "${snapshot.child("description").value}"
                    val downloadsCount = "${snapshot.child("downloadsCount").value}"
                    val timestamp = "${snapshot.child("timestamp").value}"
                    bookTitle = "${snapshot.child("title").value}"
                    val uid = "${snapshot.child("uid").value}"
                    bookUrl = "${snapshot.child("url").value}"
                    val viewsCount = "${snapshot.child("viewsCount").value}"

                    val date = MyApplication.formatTimestamp(timestamp.toLong())

                    MyApplication.loadCategory(categoryId, binding.categoryTv)
                    MyApplication.loadPdfFromUrlSinglePage(
                        "$bookUrl",
                        "$bookTitle",
                        binding.pdfView,
                        binding.progressBar,
                        binding.pagesTv
                    )
                    MyApplication.loadPdfSize("$bookUrl", "$bookTitle", binding.sizeTv)

                    binding.titleTv.text = bookTitle
                    binding.descriptionTv.text = description
                    binding.dateTv.text = date
                    binding.viewsTv.text = viewsCount
                    binding.downloadsTv.text = downloadsCount
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }
}