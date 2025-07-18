package com.example.look_a_bird.ui.addpost

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.look_a_bird.MainActivity
import com.example.look_a_bird.R
import com.example.look_a_bird.api.ApiRepository
import com.example.look_a_bird.api.ApiResult
import com.example.look_a_bird.api.BirdSpecies
import com.example.look_a_bird.database.Repository
import com.example.look_a_bird.model.Post
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddPostFragment : Fragment() {

    private lateinit var imagePostPreview: ImageView
    private lateinit var buttonSelectImage: Button
    private lateinit var autoCompleteBirdName: AutoCompleteTextView
    private lateinit var editTextDescription: EditText
    private lateinit var buttonSavePost: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var birdSearchProgress: ProgressBar

    private val apiRepository = ApiRepository.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var repository: Repository

    private var selectedImageUri: String = ""
    private var selectedBird: BirdSpecies? = null
    private var searchJob: Job? = null
    private var isSelecting = false

    private val birdSuggestionsMap = linkedMapOf<String, BirdSpecies>()
    private lateinit var birdAdapter: ArrayAdapter<String>

    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    companion object {
        private const val REQUEST_IMAGE_PICK = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_post, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = (requireActivity() as MainActivity).getRepository()

        setupViews(view)
        setupBirdSearch()
        setupClickListeners()
        getCurrentLocation()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedImageUri = uri.toString()
                imagePostPreview.setImageURI(uri)
            }
        }
    }

    private fun setupViews(view: View) {
        imagePostPreview = view.findViewById(R.id.image_post_preview)
        buttonSelectImage = view.findViewById(R.id.button_select_image)
        autoCompleteBirdName = view.findViewById(R.id.auto_complete_bird_name)
        editTextDescription = view.findViewById(R.id.edit_text_description)
        buttonSavePost = view.findViewById(R.id.button_save_post)
        progressBar = view.findViewById(R.id.progress_bar)
        birdSearchProgress = view.findViewById(R.id.bird_search_progress)
    }

    private fun setupBirdSearch() {
        birdAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )
        autoCompleteBirdName.setAdapter(birdAdapter)
        autoCompleteBirdName.threshold = 2

        autoCompleteBirdName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isSelecting) return
                val query = s.toString().trim()
                if (query.length >= 2) {
                    searchBirds(query)
                } else if (query.isEmpty()) {
                    selectedBird = null
                }
            }
        })

        autoCompleteBirdName.setOnItemClickListener { _, _, position, _ ->
            isSelecting = true
            val selectedName = birdAdapter.getItem(position)
            val selected = birdSuggestionsMap[selectedName]
            if (selected != null) {
                selectedBird = selected
                autoCompleteBirdName.setText(selected.commonName, false)
                autoCompleteBirdName.clearFocus()
                autoCompleteBirdName.dismissDropDown()
                Toast.makeText(context, "Selected: ${selected.commonName}", Toast.LENGTH_SHORT).show()
            }
            view?.postDelayed({ isSelecting = false }, 100)
        }
    }

    private fun updateBirdSuggestions(birds: List<BirdSpecies>) {
        birdSuggestionsMap.clear()
        for (bird in birds) {
            birdSuggestionsMap[bird.commonName] = bird
        }
        birdAdapter.clear()
        birdAdapter.addAll(birdSuggestionsMap.keys)
        birdAdapter.notifyDataSetChanged()
        if (autoCompleteBirdName.hasFocus()) {
            autoCompleteBirdName.showDropDown()
        }
    }

    private fun searchBirds(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(300)
            birdSearchProgress.visibility = View.VISIBLE
            try {
                when (val result = apiRepository.searchBirds(query)) {
                    is ApiResult.Success -> updateBirdSuggestions(result.data)
                    is ApiResult.Error -> Toast.makeText(context, "Search error: ${result.message}", Toast.LENGTH_SHORT).show()
                    else -> {}
                }
            } finally {
                birdSearchProgress.visibility = View.GONE
            }
        }
    }

    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    location?.let {
                        currentLatitude = it.latitude
                        currentLongitude = it.longitude
                        Toast.makeText(context, "Location acquired", Toast.LENGTH_SHORT).show()
                    } ?: Toast.makeText(context, "Unable to get location", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupClickListeners() {
        buttonSelectImage.setOnClickListener { selectImage() }
        buttonSavePost.setOnClickListener { savePost() }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    private fun savePost() {
        val birdName = autoCompleteBirdName.text.toString().trim()
        val description = editTextDescription.text.toString().trim()

        if (!validateInput(birdName, description)) return

        showLoading(true)
        savePostInternal(birdName, description)
    }

    private fun savePostInternal(birdName: String, description: String) {
        val userId = auth.currentUser?.uid ?: ""

        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val userName = doc.getString("name") ?: "Anonymous"
                val userProfileImage = doc.getString("profileImageUrl") ?: ""

                if (selectedImageUri.isNotEmpty()) {
                    uploadImageAndSavePost(birdName, description, userId, userName, userProfileImage)
                } else {
                    savePostToRepository(birdName, description, userId, userName, userProfileImage, "")
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(context, "Error fetching user profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun uploadImageAndSavePost(
        birdName: String,
        description: String,
        userId: String,
        userName: String,
        userProfileImage: String
    ) {
        val storageRef = storage.reference.child("post_images/${System.currentTimeMillis()}.jpg")

        storageRef.putFile(Uri.parse(selectedImageUri))
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: Exception("Upload failed")
                }
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                savePostToRepository(birdName, description, userId, userName, userProfileImage, downloadUri.toString())
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(context, "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun savePostToRepository(
        birdName: String,
        description: String,
        userId: String,
        userName: String,
        userProfileImage: String,
        imageUrl: String
    ) {
        val newPost = Post(
            id = "",
            userId = userId,
            userName = userName,
            userProfileImage = userProfileImage,
            birdSpecies = birdName,
            scientificName = selectedBird?.scientificName ?: "",
            description = description,
            imageUrl = imageUrl,
            latitude = currentLatitude,
            longitude = currentLongitude,
            timestamp = System.currentTimeMillis() / 1000,
            lastUpdated = System.currentTimeMillis() / 1000
        )

        lifecycleScope.launch {
            try {
                repository.addPost(newPost)
                showLoading(false)
                Toast.makeText(context, "Post saved successfully!", Toast.LENGTH_SHORT).show()
                clearForm()
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(context, "Error saving post: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun validateInput(birdName: String, description: String): Boolean {
        if (birdName.isEmpty()) {
            autoCompleteBirdName.error = "Bird name is required"
            return false
        }
        if (description.isEmpty()) {
            editTextDescription.error = "Description is required"
            return false
        }
        return true
    }

    private fun clearForm() {
        autoCompleteBirdName.text?.clear()
        editTextDescription.text?.clear()
        selectedImageUri = ""
        selectedBird = null
        imagePostPreview.setImageResource(android.R.drawable.ic_menu_camera)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        buttonSavePost.isEnabled = !show
        buttonSelectImage.isEnabled = !show
        autoCompleteBirdName.isEnabled = !show
    }
}