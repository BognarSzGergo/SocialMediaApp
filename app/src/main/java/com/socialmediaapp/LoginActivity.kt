package com.socialmediaapp

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_login.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*


class LoginActivity : AppCompatActivity() {
    val READIMAGE:Int=253
    val PICK_IMAGE_CODE:Int=123

    private lateinit var mAuth: FirebaseAuth

    private var database=FirebaseDatabase.getInstance()
    private  var myRef=database.reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth=FirebaseAuth.getInstance()

        ivImagePerson.setOnClickListener(View.OnClickListener {
            checkPermission()
        })

        FirebaseMessaging.getInstance().subscribeToTopic("news")
    }

    private fun loginToFireBase(email:String, password:String){
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this){task ->
            if (task.isSuccessful){
                Toast.makeText(applicationContext,"Successful login", Toast.LENGTH_LONG).show()
                val currentUser=mAuth.currentUser
                if(currentUser!=null){
                    saveImageInFirebase(currentUser)
                }
            }else{
                Toast.makeText(applicationContext,"Fail login", Toast.LENGTH_LONG).show()

            }
        }
    }

    fun buLoginEvent(view: View) {
        loginToFireBase(etEmail.text.toString(),etPassword.text.toString())

    }

    private fun saveImageInFirebase(currentUser:FirebaseUser) {
        val email = currentUser.email.toString()
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.getReferenceFromUrl("gs://socialmediaapp-cacf9.appspot.com")
        val df = SimpleDateFormat("ddMMyyHHmmss")
        val dateObj = Date()
        val imagePath = splitEmail(email) + df.format(dateObj) + ".jpg"
        val imageRef = storageRef.child("images/$imagePath")

        val bitmap = getBitmapFromView(ivImagePerson)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()
        val uploadTask = imageRef.putBytes(data)
        uploadTask.continueWith {
            if (!it.isSuccessful) {
                Toast.makeText(this, "Fail to upload image", Toast.LENGTH_LONG).show()
            }
            imageRef.downloadUrl
        }.addOnCompleteListener {
            if (it.isSuccessful) {
                it.result!!.addOnSuccessListener { task ->
                    val downloadURL = task.toString()
                    myRef.child("Users").child(currentUser.uid).child("Email")
                        .setValue(currentUser.email)
                    myRef.child("Users").child(currentUser.uid).child("ProfileImage")
                        .setValue(downloadURL)
                    loadPosts()
                }

            }
        }
    }
    private fun loadPosts(){
        val currentUser=mAuth.currentUser
        if (currentUser!=null){
            val intent=Intent(this,MainActivity::class.java)
            intent.putExtra("email", currentUser.email)
            intent.putExtra("uid", currentUser.uid)
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        loadPosts()
    }

    private fun splitEmail(email: String):String{
        val split=email.split("@")
        return split[0]
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap =
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun checkPermission(){
        if(Build.VERSION.SDK_INT>=23){
            if (ActivityCompat.checkSelfPermission(this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE), READIMAGE)
                return
            }
        }

        loadImage()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray){

        when(requestCode){
            READIMAGE->{if (grantResults[0]==PackageManager.PERMISSION_GRANTED){
                loadImage()
            }else{
                Toast.makeText(this,"Cannot access your images",Toast.LENGTH_LONG).show()
            }}
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun loadImage(){
        val intent=Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent,PICK_IMAGE_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode==PICK_IMAGE_CODE && data != null && resultCode == Activity.RESULT_OK){
            val selectedImage=data.data
            if (selectedImage != null){
                val filePathColumn= arrayOf(MediaStore.Images.Media.DATA)
                val cursor=contentResolver.query(selectedImage, filePathColumn, null, null, null)
                if(cursor != null){
                    cursor.moveToFirst()
                    val columnIndex=cursor.getColumnIndex(filePathColumn[0])
                    val picturePath=cursor.getString(columnIndex)
                    cursor.close()

                    ivImagePerson.setImageBitmap(BitmapFactory.decodeFile(picturePath))
                }
            }
        }
    }
}
