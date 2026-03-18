# LiveAI Project Guidelines

## Kotlin Style: Explicit Over Truthy/Falsy

Kotlin has no truthy/falsy coercion like JavaScript, but implicit conversions and smart casts can still obscure intent. Always be explicit.

### Rules

- **Null checks**: Use explicit comparisons, not reliance on smart casts across scopes
- **Boolean conditions**: Always compare to `true`/`false` when the variable could be nullable (`Boolean?`)
- **Collection checks**: Use named methods (`isEmpty()`, `isNotEmpty()`) instead of size comparisons
- **String checks**: Use `isNullOrEmpty()` or `isNullOrBlank()` — never check length manually
- **Type checks**: Use explicit `is` checks, not implicit casting

### Examples

```kotlin
// BAD - relies on nullable Boolean smart cast
if (isReady) { ... }

// GOOD - explicit null-safe check
if (isReady == true) { ... }

// BAD - implicit
val name = user?.name ?: ""
if (name != "") { ... }

// GOOD - explicit
val name = user?.name
if (!name.isNullOrBlank()) { ... }

// BAD - implicit emptiness
if (items.size > 0) { ... }

// GOOD - explicit intent
if (items.isNotEmpty()) { ... }

// BAD - nested smart casts that obscure flow
user?.let { it.profile?.let { p -> doThing(p) } }

// GOOD - explicit null check with early return
val profile = user?.profile ?: return
doThing(profile)

// BAD - implicit conversion in when
when {
    result -> handleSuccess()
}

// GOOD - explicit boolean
when {
    result == true -> handleSuccess()
    result == false -> handleFailure()
    result == null -> handleUnknown()
}
```

## Architecture: MVVM + Unidirectional Data Flow

All features follow MVVM with strict unidirectional data flow. UI never modifies state directly.

### Data Flow

```
UI (Compose) --[UiEvent]--> ViewModel --[updates]--> StateFlow<UiState> --[collects]--> UI
```

### Structure Per Feature

```
feature/
  featurename/
    FeatureNameScreen.kt       // Composable UI, observes state, sends events
    FeatureNameViewModel.kt    // Processes events, updates state
    FeatureNameUiState.kt      // Sealed interface for UI state
    FeatureNameUiEvent.kt      // Sealed interface for user actions
```

### UiState Pattern

```kotlin
sealed interface FeatureUiState {
    data object Loading : FeatureUiState
    data class Success(
        val items: List<Item> = emptyList(),
        val isRefreshing: Boolean = false
    ) : FeatureUiState
    data class Error(val message: String) : FeatureUiState
}
```

### UiEvent Pattern

```kotlin
sealed interface FeatureUiEvent {
    data object Refresh : FeatureUiEvent
    data class ItemClicked(val itemId: String) : FeatureUiEvent
    data class SearchQueryChanged(val query: String) : FeatureUiEvent
}
```

### ViewModel Pattern

```kotlin
class FeatureViewModel(
    private val repository: FeatureRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeatureUiState>(FeatureUiState.Loading)
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    fun onEvent(event: FeatureUiEvent) {
        when (event) {
            is FeatureUiEvent.Refresh -> refresh()
            is FeatureUiEvent.ItemClicked -> navigateToItem(event.itemId)
            is FeatureUiEvent.SearchQueryChanged -> search(event.query)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                when (currentState) {
                    is FeatureUiState.Success -> currentState.copy(isRefreshing = true)
                    else -> FeatureUiState.Loading
                }
            }
            repository.getItems()
                .onSuccess { items ->
                    _uiState.value = FeatureUiState.Success(items = items)
                }
                .onFailure { error ->
                    _uiState.value = FeatureUiState.Error(message = error.message ?: "Unknown error")
                }
        }
    }
}
```

### Screen (Composable) Pattern

```kotlin
@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FeatureContent(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun FeatureContent(
    uiState: FeatureUiState,
    onEvent: (FeatureUiEvent) -> Unit
) {
    when (uiState) {
        is FeatureUiState.Loading -> LoadingIndicator()
        is FeatureUiState.Success -> {
            ItemList(
                items = uiState.items,
                onItemClick = { id -> onEvent(FeatureUiEvent.ItemClicked(id)) }
            )
        }
        is FeatureUiState.Error -> ErrorMessage(
            message = uiState.message,
            onRetry = { onEvent(FeatureUiEvent.Refresh) }
        )
    }
}
```

### Rules

- **State flows DOWN**: ViewModel -> UI via `StateFlow`
- **Events flow UP**: UI -> ViewModel via `onEvent()`
- **No mutable state in Composables**: All state lives in ViewModel
- **Stateless Composables**: Pass state and callbacks as parameters (see `FeatureContent` above)
- **Single source of truth**: One `UiState` per screen, one `StateFlow` per ViewModel
- **No side effects in UI**: Navigation, toasts, snackbars triggered via one-time events (Channel/SharedFlow)

## Manual DI Pattern

Use an `AppContainer` created in `Application` and passed down explicitly — no Hilt, no Koin, no framework.

```kotlin
// Container holds dependencies
class AppContainer(
    private val context: Context
) {
    val featureRepository: FeatureRepository by lazy {
        FeatureRepositoryImpl(apiService)
    }

    private val apiService: ApiService by lazy {
        ApiServiceImpl(context)
    }
}

// Application creates the container
class LiveAIApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

// Activity wires ViewModels manually
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LiveAIApplication).container

        setContent {
            val viewModel = viewModel {
                FeatureViewModel(container.featureRepository)
            }
            FeatureScreen(viewModel = viewModel)
        }
    }
}
```

### Rules

- Dependencies flow through constructor parameters
- `AppContainer` is the single composition root
- Use `lazy` for expensive dependencies
- ViewModels are created via `viewModel { }` factory, not framework injection
- No annotations, no code generation, no magic

## Overlay Architecture

This app uses **system overlays** (drawing over other apps) for chat UI, floating buttons, message bars, and warning windows. Overlays run from a foreground `Service`, not an `Activity`.

### How It Works

```
Service (foreground)
  └── WindowManager.addView(ComposeView)
        └── Compose UI (chat overlay, buttons, alerts)
```

### Overlay Service Pattern

```kotlin
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val overlayViews = mutableListOf<View>()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayViews.forEach { windowManager.removeView(it) }
        overlayViews.clear()
        super.onDestroy()
    }

    fun addOverlay(
        content: @Composable () -> Unit,
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        gravity: Int = Gravity.CENTER,
        flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    ) {
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent { content() }
        }

        windowManager.addView(composeView, params)
        overlayViews.add(composeView)
    }

    private val windowManager: WindowManager
        get() = getSystemService(WINDOW_SERVICE) as WindowManager
}
```

### Overlay Types

| Overlay | Flags | Notes |
|---------|-------|-------|
| Chat bubble | `FLAG_NOT_FOCUSABLE` | Transparent background, draggable |
| Message bar | `FLAG_NOT_FOCUSABLE` | Bottom-anchored, auto-dismiss |
| Floating buttons | `FLAG_NOT_FOCUSABLE` | Draggable, small touch target |
| Warning window | `FLAG_NOT_TOUCH_MODAL` | Needs focus for user input |

### Transparency

```kotlin
// In Compose, use transparent backgrounds
Surface(
    color = Color.Transparent,
    modifier = Modifier.background(
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp)
    )
) {
    // overlay content
}
```

### Required Permission

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

Request at runtime:
```kotlin
if (!Settings.canDrawOverlays(context)) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    startActivity(intent)
}
```

### Rules

- All overlays run from a **foreground Service** with a persistent notification
- Use `ComposeView` + `setViewTreeLifecycleOwner` to get Compose working in Service context
- Always clean up views in `onDestroy()` — leaked overlays persist after app death
- Use `FLAG_NOT_FOCUSABLE` by default — only use `FLAG_NOT_TOUCH_MODAL` when the overlay needs text input
- Check `Settings.canDrawOverlays()` before showing any overlay
- Transparent overlays need `PixelFormat.TRANSLUCENT` in LayoutParams

## Tech Stack

- **UI**: Jetpack Compose
- **DI**: Manual (constructor injection, no framework)
- **Navigation**: Compose Navigation
- **State**: StateFlow + collectAsStateWithLifecycle
- **Async**: Kotlin Coroutines + Flow
- **Build**: Gradle with version catalog (libs.versions.toml)
