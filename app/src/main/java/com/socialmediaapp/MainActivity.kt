package com.socialmediaapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.add_ticket.view.*
import kotlinx.android.synthetic.main.posts_ticket.view.*
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    val PICK_IMAGE_CODE=123

    private var database= FirebaseDatabase.getInstance()
    private var myRef=database.reference

    val listPosts=ArrayList<Ticket>()
    lateinit var adapter:MyPostAdapter

    var userEmail:String?=null
    var userUID:String?=null

    var downloadURL:String=""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bundle=intent.extras
        userEmail=bundle?.getString("email")
        userUID=bundle?.getString("uid")


        listPosts.add(Ticket("0","him","url", "add", "date"))

        adapter=MyPostAdapter(this,listPosts)
        lvPosts.adapter=adapter
        loadPost()
    }

    inner class  MyPostAdapter(val context: Context, val listNotesAdpater: ArrayList<Ticket>) : BaseAdapter() {

        override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
            val myPost=listNotesAdpater[p0]

            if(myPost.postPersonUID == "add") {
                val myView = layoutInflater.inflate(R.layout.add_ticket, null)

                myView.iv_attach.setOnClickListener(View.OnClickListener {
                    loadImage()
                })

                myView.iv_post.setOnClickListener(View.OnClickListener {
                    val df = SimpleDateFormat("yyyy-MM-dd")
                    val dateObj = Date()
                    val creationDate = df.format(dateObj)
                    myRef.child("Posts").push().setValue(PostInfo(userUID!!, myView.etPost.text.toString(), downloadURL,creationDate))
                    myView.etPost.setText("")
                })

                return myView
            }
            else if(myPost.postPersonUID.equals("loading")) {
                val myView = layoutInflater.inflate(R.layout.loading_ticket, null)

                return myView
            }else{
                val myView = layoutInflater.inflate(R.layout.posts_ticket, null)
                myView.txt_post.text = myPost.postText
                Picasso.get().load(myPost.postImageURL).into(myView.post_picture)
                myView.txt_post_date.text=myPost.creationDate
                myRef.child("Users").child(myPost.postPersonUID).addValueEventListener(object :ValueEventListener{
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        try {

                            val td=dataSnapshot.value as HashMap<String,Any>
                            for (key in td.keys) {
                                val userInfo = td[key] as String
                                if (key=="ProfileImage"){
                                    Picasso.get().load(userInfo).into(myView.picture_path)
                                }else{
                                    myView.txtUserName.text = userInfo
                                }
                            }
                        }catch (ex:Exception){}
                    }

                    override fun onCancelled(p0: DatabaseError) {

                    }

                })

                return myView
            }
        }

        override fun getItem(p0: Int): Any {
            return listNotesAdpater[p0]
        }

        override fun getItemId(p0: Int): Long {
            return p0.toLong()
        }

        override fun getCount(): Int {
            return listNotesAdpater.size
        }
    }


    fun loadImage(){

        val intent= Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_CODE)
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

                    uploadImage(BitmapFactory.decodeFile(picturePath))
                }
            }
        }
    }

    private fun uploadImage(bitmap: Bitmap) {
        val loadingTicket=Ticket("0","him","url","loading", "date")
        listPosts.add(loadingTicket)
        adapter.notifyDataSetChanged()

        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.getReferenceFromUrl("gs://socialmediaapp-cacf9.appspot.com")
        val df = SimpleDateFormat("ddMMyyHHmmss")
        val dateobj = Date()
        val imagePath = splitEmail(userEmail!!) + df.format(dateobj) + ".jpg"
        val imageRef = storageRef.child("imagesPost/$imagePath")

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
                    downloadURL = task.toString()
                    listPosts.remove(loadingTicket)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun splitEmail(email: String):String{
        val split=email.split("@")
        return split[0]
    }

    private fun loadPost(){
        myRef.child("Posts").addValueEventListener(object :ValueEventListener{
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                try {
                    listPosts.clear()
                    listPosts.add(Ticket("0","him","url", "add","date"))
                    val td=dataSnapshot.value as HashMap<String,Any>
                    for (key in td.keys){
                        val post=td[key] as HashMap<String,Any>

                        listPosts.add(Ticket(
                            key,
                            post["text"] as String,
                            post["imageURL"] as String,
                            post["userUID"] as String,
                            post["creationDate"] as String)
                        )
                    }
                    adapter.notifyDataSetChanged()
                }catch (ex:Exception){}
            }
            override fun onCancelled(p0: DatabaseError) {

            }

        })
    }

}
