package uk.lauragilbert.todo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { TodoTheme { TodoScreen() } }
    }
}

@Composable
fun TodoTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Palette[7],
        onPrimary = SlateInk,
        surface = Slate,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFB4BCC8),
        surfaceContainerHighest = SlateLight,
        inverseSurface = Color(0xFFF1F3F6),
        inverseOnSurface = SlateInk,
        inversePrimary = Palette[9],
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun TodoScreen(vm: TodoViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshDay()
        onPauseOrDispose { }
    }
    var input by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        vm.move(from.index, to.index)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    fun add(text: String) {
        vm.add(text)
        scope.launch { listState.animateScrollToItem(maxOf(0, vm.items.value.size - 1)) }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::add)
        }
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say an item")
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Voice input isn't available on this phone") }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            InputBar(
                value = input,
                onValueChange = { input = it },
                onSubmit = {
                    if (input.isNotBlank()) { add(input); input = "" }
                },
                onMic = ::listen,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
        ) {
            Row(
                Modifier.padding(start = 24.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "To do",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (items.isEmpty()) "Nothing left" else "${items.size} ${if (items.size == 1) "item" else "items"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Counter(stats.today, "Today", Palette[5])
                Spacer(Modifier.width(12.dp))
                Counter(stats.total, "All time", Palette[9])
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Type or tap the mic to add something",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { todo ->
                      ReorderableItem(reorderState, key = todo.id) { dragging ->
                        TodoRow(
                            todo = todo,
                            dragScope = this,
                            dragging = dragging,
                            onEdit = { vm.edit(todo, it) },
                            onDelete = {
                                val index = vm.remove(todo)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val r = snackbar.showSnackbar(
                                        "Done", actionLabel = "Undo", duration = SnackbarDuration.Short,
                                    )
                                    if (r == SnackbarResult.ActionPerformed) vm.restore(todo, index)
                                }
                            },
                        )
                      }
                    }
                }
            }
        }
    }
}

@Composable
fun Counter(value: Int, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(SlateLight)
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = label,
            ) { n ->
                Text(
                    if (n > 9999) "${n / 1000}k" else n.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodoRow(
    todo: Todo,
    dragScope: ReorderableCollectionItemScope,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val lift by animateDpAsState(if (dragging) 10.dp else 0.dp, label = "lift")
    val grow by animateFloatAsState(if (dragging) 1.03f else 1f, label = "grow")
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue(todo.text)) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }

    fun finish() {
        if (!editing) return
        editing = false
        hadFocus = false
        onEdit(draft.text)
        focusManager.clearFocus()
    }

    BackHandler(enabled = editing) { finish() }
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }

    val imeVisible = WindowInsets.isImeVisible
    var hadIme by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, editing) {
        if (!editing) hadIme = false
        else if (imeVisible) hadIme = true
        else if (hadIme) finish()
    }
    val latestDraft by rememberUpdatedState(draft.text)
    val latestEditing by rememberUpdatedState(editing)
    LifecycleResumeEffect(Unit) {
        onPauseOrDispose { if (latestEditing) onEdit(latestDraft) }
    }

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it != SwipeToDismissBoxValue.Settled) onDelete()
            true
        },
        positionalThreshold = { it * 0.35f },
    )
    val shape = RoundedCornerShape(18.dp)
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        gesturesEnabled = !editing,
        backgroundContent = {
            val active = state.targetValue != SwipeToDismissBoxValue.Settled
            val iconScale by animateFloatAsState(if (active) 1.15f else 0.85f, label = "icon")
            val alignment = if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                Alignment.CenterEnd else Alignment.CenterStart
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        if (state.dismissDirection == SwipeToDismissBoxValue.Settled) Color.Transparent
                        else SlateLight
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = Palette[todo.color],
                    modifier = Modifier.scale(iconScale),
                )
            }
        },
    ) {
        Surface(
            onClick = {
                draft = TextFieldValue(todo.text, selection = TextRange(todo.text.length))
                editing = true
            },
            enabled = !editing,
            shape = shape,
            color = Palette[todo.color],
            shadowElevation = lift,
            modifier = with(dragScope) {
                Modifier
                    .fillMaxWidth()
                    .scale(grow)
                    .longPressDraggableHandle(
                        enabled = !editing,
                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    )
            },
        ) {
            val textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = SlateInk)
            val textModifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
            if (editing) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = textStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(SlateInk),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { finish() }),
                    modifier = textModifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged {
                            if (it.isFocused) hadFocus = true
                            else if (hadFocus) finish()
                        },
                )
            } else {
                Text(todo.text, style = textStyle, modifier = textModifier)
            }
        }
    }
}

@Composable
fun InputBar(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit, onMic: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Add an item") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            val hasText = value.isNotBlank()
            FilledIconButton(
                onClick = { if (hasText) onSubmit() else onMic() },
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = SlateInk,
                ),
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Palette[0], Palette[3], Palette[6], Palette[9]))),
            ) {
                AnimatedContent(
                    targetState = hasText,
                    transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) },
                    label = "action",
                ) { text ->
                    if (text) Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Add")
                    else Icon(Icons.Rounded.Mic, contentDescription = "Speak an item")
                }
            }
        }
    }
}
