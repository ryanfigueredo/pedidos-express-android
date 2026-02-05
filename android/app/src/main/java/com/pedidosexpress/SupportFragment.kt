package com.pedidosexpress

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SupportFragment : Fragment() {
    private lateinit var apiService: ApiService
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var whatsappButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var progressBar: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyStateView: View
    private lateinit var chatContainer: View
    private lateinit var titleAtendimento: TextView
    
    private var conversations = mutableListOf<PriorityConversation>()
    private var selectedConversation: PriorityConversation? = null
    private var bottomNavigation: com.google.android.material.bottomnavigation.BottomNavigationView? = null
    private var messages = mutableListOf<ChatMessage>()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_support, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        apiService = ApiService(requireContext())
        
        conversationsRecyclerView = view.findViewById(R.id.conversations_recycler)
        messagesRecyclerView = view.findViewById(R.id.messages_recycler)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)
        whatsappButton = view.findViewById(R.id.whatsapp_button)
        backButton = view.findViewById(R.id.back_button)
        progressBar = view.findViewById(R.id.progress_bar)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        emptyStateView = view.findViewById(R.id.empty_state)
        chatContainer = view.findViewById(R.id.chat_container)
        titleAtendimento = view.findViewById(R.id.title_atendimento)
        
        conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        messagesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        conversationsRecyclerView.adapter = ConversationsAdapter(conversations) { conversation ->
            selectConversation(conversation)
        }
        
        messagesRecyclerView.adapter = MessagesAdapter(messages)
        
        sendButton.setOnClickListener {
            sendMessage()
        }
        
        whatsappButton.setOnClickListener {
            openWhatsApp()
        }
        
        backButton.setOnClickListener {
            goBackToConversationsList()
        }
        
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sendButton.isEnabled = !s.toString().trim().isEmpty()
            }
        })
        
        swipeRefresh.setOnRefreshListener {
            loadConversations()
        }
        
        // Buscar referência ao bottom navigation da activity
        activity?.let {
            if (it is MainNavigationActivity) {
                bottomNavigation = it.findViewById(R.id.bottom_navigation)
            }
        }
        
        // Detectar quando o teclado abre/fecha e esconder/mostrar bottom nav
        setupKeyboardListener(view)
        
        loadConversations()
        startAutoRefresh()
    }
    
    private fun setupKeyboardListener(rootView: View) {
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            // Verificar se o fragment ainda está anexado
            if (!isAdded || context == null) return@addOnGlobalLayoutListener
            
            // Verificar se o teclado está aberto
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            
            // Só esconder bottom nav quando o teclado estiver aberto
            if (keypadHeight > 200.dpToPx(requireContext())) {
                bottomNavigation?.visibility = View.GONE
            } else {
                bottomNavigation?.visibility = View.VISIBLE
            }
        }
    }
    
    private fun Int.dpToPx(context: android.content.Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoRefresh()
    }
    
    private fun loadConversations() {
        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val loadedConversations = withContext(Dispatchers.IO) {
                    apiService.getPriorityConversations()
                }
                
                conversations.clear()
                conversations.addAll(loadedConversations)
                
                (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
                
                if (conversations.isEmpty()) {
                    emptyStateView.visibility = View.VISIBLE
                    chatContainer.visibility = View.GONE
                    titleAtendimento.visibility = View.VISIBLE
                } else {
                    emptyStateView.visibility = View.GONE
                    if (selectedConversation == null) {
                        chatContainer.visibility = View.GONE
                        titleAtendimento.visibility = View.VISIBLE
                    } else {
                        chatContainer.visibility = View.VISIBLE
                        titleAtendimento.visibility = View.GONE
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SupportFragment", "Erro ao carregar conversas", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }
    
    private fun goBackToConversationsList() {
        selectedConversation = null
        chatContainer.visibility = View.GONE
        
        // Mostrar título "Atendimento" quando voltar para lista
        titleAtendimento.visibility = View.VISIBLE
        
        // Limpar seleção na lista de conversas
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.selectedPhone = null
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
    }
    
    private fun selectConversation(conversation: PriorityConversation) {
        selectedConversation = conversation
        chatContainer.visibility = View.VISIBLE
        
        // Esconder título "Atendimento" quando uma conversa está selecionada
        titleAtendimento.visibility = View.GONE
        
        // Atualizar header do chat com telefone formatado
        val headerPhone = view?.findViewById<TextView>(R.id.chat_header_phone)
        headerPhone?.text = conversation.phoneFormatted
        
        // Mostrar botão WhatsApp
        whatsappButton.visibility = View.VISIBLE
        
        // Mensagem inicial informando que o cliente pediu atendimento
        messages.clear()
        messages.add(ChatMessage(
            id = "0",
            text = "Cliente pediu atendimento humano pelo bot.",
            isAttendant = false,
            timestamp = Date(conversation.timestamp)
        ))
        
        (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyDataSetChanged()
        scrollToBottom()
        
        // Atualizar lista de conversas para destacar selecionada
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.selectedPhone = conversation.phone
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
    }
    
    private fun openWhatsApp() {
        val conversation = selectedConversation ?: return
        
        try {
            val whatsappUrl = conversation.whatsappUrl
            if (whatsappUrl.isNotEmpty()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(whatsappUrl))
                startActivity(intent)
            } else {
                // Fallback: construir URL manualmente
                var phone = conversation.phone.replace(Regex("[^0-9]"), "")
                if (!phone.startsWith("55") && phone.length >= 10) {
                    phone = "55$phone"
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$phone"))
                startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportFragment", "Erro ao abrir WhatsApp", e)
            Toast.makeText(requireContext(), "Erro ao abrir WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        val conversation = selectedConversation
        
        if (text.isEmpty() || conversation == null) return
        
        messageInput.setText("")
        sendButton.isEnabled = false
        
        val tempId = "temp-${System.currentTimeMillis()}"
        val newMessage = ChatMessage(
            id = tempId,
            text = text,
            isAttendant = true,
            timestamp = Date(),
            status = ChatMessageStatus.SENDING
        )
        
        messages.add(newMessage)
        (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyItemInserted(messages.size - 1)
        scrollToBottom()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Formatar telefone
                var phone = conversation.phone.replace(Regex("[^0-9]"), "")
                if (!phone.startsWith("55") && phone.length >= 10) {
                    phone = "55$phone"
                }
                
                val success = withContext(Dispatchers.IO) {
                    apiService.sendWhatsAppMessage(phone, text)
                }
                
                val messageIndex = messages.indexOfFirst { it.id == tempId }
                if (messageIndex >= 0) {
                    messages[messageIndex] = messages[messageIndex].copy(
                        status = if (success) ChatMessageStatus.SENT else ChatMessageStatus.ERROR
                    )
                    (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyItemChanged(messageIndex)
                }
                
                if (!success) {
                    Toast.makeText(requireContext(), "Erro ao enviar mensagem", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SupportFragment", "Erro ao enviar mensagem", e)
                val messageIndex = messages.indexOfFirst { it.id == tempId }
                if (messageIndex >= 0) {
                    messages[messageIndex] = messages[messageIndex].copy(status = ChatMessageStatus.ERROR)
                    (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyItemChanged(messageIndex)
                }
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                sendButton.isEnabled = true
            }
        }
    }
    
    private fun scrollToBottom() {
        messagesRecyclerView.post {
            if (messages.isNotEmpty()) {
                messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
            }
        }
    }
    
    private fun startAutoRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                loadConversations()
                refreshHandler.postDelayed(this, 10000) // Atualiza a cada 10 segundos
            }
        }
        refreshHandler.postDelayed(refreshRunnable!!, 10000)
    }
    
    private fun stopAutoRefresh() {
        refreshRunnable?.let {
            refreshHandler.removeCallbacks(it)
        }
    }
}

data class ChatMessage(
    val id: String,
    val text: String,
    val isAttendant: Boolean,
    val timestamp: Date,
    val status: ChatMessageStatus = ChatMessageStatus.SENT
)

enum class ChatMessageStatus {
    SENDING,
    SENT,
    ERROR
}
