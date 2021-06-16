package org.thoughtcrime.securesms.conversation.v2

import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.database.Cursor
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import kotlinx.android.synthetic.main.activity_conversation_v2_action_bar.*
import kotlinx.android.synthetic.main.view_input_bar.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarDelegate
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationActionModeCallback
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationMenuHelper
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.views.NewConversationButtonSetView
import org.thoughtcrime.securesms.mms.GlideApp

class ConversationActivityV2 : PassphraseRequiredActionBarActivity(), InputBarDelegate {
    private var threadID: Long = -1
    private var actionMode: ActionMode? = null

    // TODO: Selected message background color
    // TODO: Overflow menu background + text color

    private val adapter by lazy {
        val cursor = DatabaseFactory.getMmsSmsDatabase(this).getConversation(threadID)
        val adapter = ConversationAdapter(
            this,
            cursor,
            onItemPress = { message, position ->
                handlePress(message, position)
            },
            onItemSwipeToReply = { message, position ->
                handleSwipeToReply(message, position)
            },
            onItemLongPress = { message, position ->
                handleLongPress(message, position)
            }
        )
        adapter.setHasStableIds(true)
        adapter
    }

    private val thread by lazy {
        DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadID)!!
    }

    private val glide by lazy { GlideApp.with(this) }

    // region Settings
    companion object {
        const val THREAD_ID = "thread_id"
    }
    // endregion

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_conversation_v2)
        threadID = intent.getLongExtra(THREAD_ID, -1)
        setUpRecyclerView()
        setUpToolBar()
        inputBar.delegate = this
    }

    private fun setUpRecyclerView() {
        conversationRecyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, true)
        conversationRecyclerView.layoutManager = layoutManager
        // Workaround for the fact that CursorRecyclerViewAdapter doesn't auto-update automatically (even though it says it will)
        LoaderManager.getInstance(this).restartLoader(0, null, object : LoaderManager.LoaderCallbacks<Cursor> {

            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<Cursor> {
                return ConversationLoader(threadID, this@ConversationActivityV2)
            }

            override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
                adapter.changeCursor(cursor)
            }

            override fun onLoaderReset(cursor: Loader<Cursor>) {
                adapter.changeCursor(null)
            }
        })
    }

    private fun setUpToolBar() {
        val actionBar = supportActionBar!!
        actionBar.setCustomView(R.layout.activity_conversation_v2_action_bar)
        actionBar.setDisplayShowCustomEnabled(true)
        conversationTitleView.text = thread.toShortString()
        profilePictureView.glide = glide
        profilePictureView.update(thread, threadID)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        ConversationMenuHelper.onPrepareOptionsMenu(menu, menuInflater, thread, this) { onOptionsItemSelected(it) }
        super.onPrepareOptionsMenu(menu)
        return true
    }
    // endregion

    // region Updating
    override fun inputBarHeightChanged(newValue: Int) {
        val recyclerViewLayoutParams = conversationRecyclerView.layoutParams as RelativeLayout.LayoutParams
        recyclerViewLayoutParams.bottomMargin = newValue
        conversationRecyclerView.layoutParams = recyclerViewLayoutParams
    }

    override fun showVoiceMessageUI() {
        inputBarRecordingView.show()
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // TODO: Implement
        return super.onOptionsItemSelected(item)
    }

    // `position` is the adapter position; not the visual position
    private fun handlePress(message: MessageRecord, position: Int) {
        val actionMode = this.actionMode
        if (actionMode != null) {
            adapter.toggleSelection(message, position)
            val actionModeCallback = ConversationActionModeCallback(adapter, threadID, this)
            actionModeCallback.updateActionModeMenu(actionMode.menu)
            if (adapter.selectedItems.isEmpty()) {
                actionMode.finish()
                this.actionMode = null
            }
        }
    }

    // `position` is the adapter position; not the visual position
    private fun handleSwipeToReply(message: MessageRecord, position: Int) {

    }

    // `position` is the adapter position; not the visual position
    private fun handleLongPress(message: MessageRecord, position: Int) {
        val actionMode = this.actionMode
        val actionModeCallback = ConversationActionModeCallback(adapter, threadID, this)
        if (actionMode == null) { // Nothing should be selected if this is the case
            adapter.toggleSelection(message, position)
            this.actionMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                startActionMode(actionModeCallback, ActionMode.TYPE_PRIMARY)
            } else {
                startActionMode(actionModeCallback)
            }
        } else {
            adapter.toggleSelection(message, position)
            actionModeCallback.updateActionModeMenu(actionMode.menu)
            if (adapter.selectedItems.isEmpty()) {
                actionMode.finish()
                this.actionMode = null
            }
        }
    }
    // endregion
}