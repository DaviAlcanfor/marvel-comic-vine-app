package com.projeto.marvel.ui.photo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

// Onde a foto com o personagem vai parar: galeria (MediaStore, sem permissão de armazenamento
// a partir do Android 10) ou compartilhamento (arquivo no cache entregue por FileProvider).

private const val JPEG_QUALITY = 92
private const val ALBUM = "Marvel"
private const val PHOTOS_DIR = "photos"

/** Arquivo novo no cache para a câmera gravar; o Uri é o que se entrega ao app de câmera. */
fun newCameraUri(context: Context): Uri {
    val file = File(File(context.cacheDir, PHOTOS_DIR).apply { mkdirs() }, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Salva em Pictures/Marvel. Devolve null se o sistema recusar. */
fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "marvel_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    return uri
}

/** Abre o seletor "Compartilhar" do sistema com a imagem. */
fun shareImage(context: Context, bitmap: Bitmap, title: String) {
    val file = File(File(context.cacheDir, PHOTOS_DIR).apply { mkdirs() }, "share.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, title))
}
