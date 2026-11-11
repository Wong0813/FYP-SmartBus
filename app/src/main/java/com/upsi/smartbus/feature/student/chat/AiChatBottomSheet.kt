package com.upsi.smartbus.feature.student.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.upsi.smartbus.core.network.AiApiClient
import com.upsi.smartbus.core.network.ChatRequest
import com.upsi.smartbus.databinding.FragmentAiChatBottomSheetBinding
import com.upsi.smartbus.databinding.ItemChatMessageAiBinding
import com.upsi.smartbus.databinding.ItemChatMessageUserBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiChatBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAiChatBottomSheetBinding? = null
    private val binding get() = _binding!!

    data class ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis())

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    companion object {
        fun newInstance(): AiChatBottomSheet = AiChatBottomSheet()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiChatBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupDialogHeight()
        setupRecyclerView()
        setupListeners()
        
        // Show welcome message
        addMessage(ChatMessage("Hello! I'm your SmartBus AI Assistant. How can I help you today?", isUser = false))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    private fun setupDialogHeight() {
        dialog?.setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout ?: return@setOnShowListener
            
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val displayMetrics = requireContext().resources.displayMetrics
            behavior.peekHeight = (displayMetrics.heightPixels * 0.75).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            
            // Allow bottomSheet to adjust its height dynamically when keyboard opens
            bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            bottomSheet.requestLayout()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(messages)
        binding.rvMessages.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.etMessage.text.clear()
            }
        }
        
        binding.chipNextBus.setOnClickListener {
            sendMessage("Next bus?")
        }
        binding.chipKabToKuo.setOnClickListener {
            sendMessage("KAB to KUO?")
        }
        binding.chipCrowded.setOnClickListener {
            sendMessage("Is it crowded?")
        }
        binding.tvChatTitle.setOnClickListener {
            showServerConfigDialog()
        }

        binding.etMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.rvMessages.postDelayed({
                    if (messages.isNotEmpty()) {
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    }
                }, 250)
            }
        }

        binding.etMessage.setOnClickListener {
            binding.rvMessages.postDelayed({
                if (messages.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(messages.size - 1)
                }
            }, 250)
        }
    }

    private fun showServerConfigDialog() {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            setText(AiApiClient.baseUrl)
            setSelection(text.length)
            hint = "http://192.168.0.104:5000"
            setPadding(40, 30, 40, 30)
        }
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("⚙️ AI Server URL")
            .setMessage("Current computer IP & port:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    AiApiClient.saveConfig(ctx, newUrl)
                    android.widget.Toast.makeText(ctx, "AI Server updated to:\n${AiApiClient.baseUrl}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendMessage(text: String) {
        addMessage(ChatMessage(text, isUser = true))
        
        // Disable input while processing
        binding.etMessage.isEnabled = false
        binding.btnSend.isEnabled = false
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val service = AiApiClient.getService()
                val callResponse = service.chat(ChatRequest(text)).execute()
                val reply = if (callResponse.isSuccessful) {
                    callResponse.body()?.response ?: "Sorry, I didn't understand that."
                } else {
                    "AI server returned error (${callResponse.code()})."
                }
                withContext(Dispatchers.Main) {
                    addMessage(ChatMessage(reply, isUser = false))
                    enableInput()
                }
            } catch (e: Exception) {
                handleError()
            }
        }
    }

    private suspend fun handleError() {
        withContext(Dispatchers.Main) {
            addMessage(ChatMessage("AI server is connecting or waking up from sleep. If on cloud, please wait ~20 seconds and try again.", isUser = false))
            enableInput()
        }
    }
    
    private fun enableInput() {
        binding.etMessage.isEnabled = true
        binding.btnSend.isEnabled = true
    }

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        binding.rvMessages.scrollToPosition(messages.size - 1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatMarkdown(raw: String): CharSequence {
        // Convert **bold** to <b>bold</b> and \n to <br/>
        val html = raw
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            .replace("\n", "<br/>")
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(html)
        }
    }

    private inner class ChatAdapter(private val messageList: List<ChatMessage>) : 
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_USER = 1
        private val VIEW_TYPE_AI = 2

        override fun getItemViewType(position: Int): Int {
            return if (messageList[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == VIEW_TYPE_USER) {
                UserViewHolder(ItemChatMessageUserBinding.inflate(inflater, parent, false))
            } else {
                AiViewHolder(ItemChatMessageAiBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val message = messageList[position]
            if (holder is UserViewHolder) {
                holder.bind(message)
            } else if (holder is AiViewHolder) {
                holder.bind(message)
            }
        }

        override fun getItemCount(): Int = messageList.size

        inner class UserViewHolder(private val binding: ItemChatMessageUserBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(message: ChatMessage) {
                binding.tvMessage.text = message.text
                binding.tvTime.text = formatTime(message.timestamp)
            }
        }

        inner class AiViewHolder(private val binding: ItemChatMessageAiBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(message: ChatMessage) {
                binding.tvMessage.text = formatMarkdown(message.text)
                binding.tvTime.text = formatTime(message.timestamp)
            }
        }

        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}
