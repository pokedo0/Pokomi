package eu.kanade.tachiyomi.ui.following

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.following.AuthorRankScreen as AuthorRankContent

class AuthorRankScreen(
    private val initialAuthorId: Long?,
    private val onSaved: (Long?) -> Unit,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AuthorRankScreenModel(initialAuthorId) }
        val state by screenModel.state.collectAsState()

        AuthorRankContent(
            state = state,
            onReorder = screenModel::reorder,
            onTogglePinned = screenModel::togglePinned,
            onMoveToTop = screenModel::moveToTop,
            onMoveToBottom = screenModel::moveToBottom,
            onDismissError = screenModel::dismissError,
            onSave = {
                screenModel.save(
                    onSaved = onSaved,
                    navigateUp = navigator::pop,
                )
            },
            navigateUp = navigator::pop,
        )
    }
}
