import androidx.compose.material3.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Test() {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        backgroundContent = {},
        content = {}
    )
}
