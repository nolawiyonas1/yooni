package com.example.yooni

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI

/**
 * Takes raw transcribed text and uses GPT-4o to format it into a clean
 * action preview the user can confirm before execution.
 *
 * Usage:
 *   val formatter = ActionFormatter("your-openai-api-key")
 *   val formatted = formatter.format("text mom that ill be there in 10 mins")
 *   // Returns: "Send a text to Mom: 'Hey! I'll be there in about 10 minutes.'"
 *
 *   val refined = formatter.refine("add do you need anything")
 *   // Returns: "Send a text to Mom: 'Hey! I'll be there in about 10 minutes. Do you need anything?'"
 */
class ActionFormatter(apiKey: String) {

    companion object {
        private const val TAG = "ActionFormatter"

        private const val SYSTEM_PROMPT = """You are Yooni, a voice assistant that controls the user's Android phone.

When the user gives a command, format it into a clear action preview. Be concise.

Rules:
- Output ONLY the formatted action, nothing else
- For messages: show the recipient and the exact message text you'll send
- For app actions: describe exactly what you'll do step by step
- Keep the tone natural and friendly
- If the command is unclear, ask one short clarifying question

Examples:
- Input: "text mom that ill be there in 10 mins"
  Output: Send a text to Mom: "Hey! I'll be there in about 10 minutes."

- Input: "open spotify and play my liked songs"
  Output: Open Spotify and play your Liked Songs playlist.

- Input: "set an alarm for 7am tomorrow"
  Output: Set an alarm for 7:00 AM tomorrow."""
    }

    private val openai = OpenAI(apiKey)
    private val conversationHistory = mutableListOf<ChatMessage>(
        ChatMessage(role = ChatRole.System, content = SYSTEM_PROMPT)
    )

    /**
     * Format a raw voice command into a clean action preview.
     */
    suspend fun format(rawCommand: String): String {
        // Reset conversation for new command (keep system prompt)
        conversationHistory.clear()
        conversationHistory.add(ChatMessage(role = ChatRole.System, content = SYSTEM_PROMPT))
        conversationHistory.add(ChatMessage(role = ChatRole.User, content = rawCommand))

        return chat()
    }

    /**
     * Refine the previous action based on user feedback.
     * Keeps conversation history so the LLM knows what to change.
     */
    suspend fun refine(feedback: String): String {
        conversationHistory.add(ChatMessage(role = ChatRole.User, content = feedback))
        return chat()
    }

    /**
     * Get the last formatted action (what mobile-use should execute).
     */
    fun getLastAction(): String {
        return conversationHistory
            .lastOrNull { it.role == ChatRole.Assistant }
            ?.content
            .orEmpty()
    }

    private suspend fun chat(): String {
        val request = ChatCompletionRequest(
            model = ModelId("gpt-4o"),
            messages = conversationHistory
        )

        val response: ChatCompletion = openai.chatCompletion(request)
        val reply = response.choices.firstOrNull()?.message?.content.orEmpty()

        // Add assistant reply to history for refinement
        conversationHistory.add(ChatMessage(role = ChatRole.Assistant, content = reply))

        Log.d(TAG, "Formatted action: $reply")
        return reply
    }
}
