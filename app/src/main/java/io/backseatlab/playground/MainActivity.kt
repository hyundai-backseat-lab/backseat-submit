package io.backseatlab.playground

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.backseatlab.playground.ui.cards.CardsScreen
import io.backseatlab.playground.ui.cards.CardsViewModel
import io.backseatlab.playground.ui.main.MainScreen
import io.backseatlab.playground.ui.main.MainViewModel
import io.backseatlab.playground.ui.theme.BackseatPlaygroundTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as App).container
                MainViewModel(container.session, container.locationConsent)
            }
        }
    }

    private val cardsViewModel: CardsViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as App).container
                CardsViewModel(container.cardStudio, container.cardsAvailable, container.cardSlots)
            }
        }
    }

    /** 녹음 화면에서 받은 마이크 권한. 허용되면 녹음 버튼을 다시 누른다. */
    private val cardsMicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onMicPermissionResult(granted)
        }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            viewModel.onLocationPermissionResult(result[Manifest.permission.ACCESS_FINE_LOCATION] == true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackseatPlaygroundTheme {
                Surface {
                    val cards = cardsViewModel.uiState.collectAsState().value
                    if (cards.open) {
                        CardsScreen(
                            state = cards,
                            onAgreeClick = { cardsViewModel.onAgreeClick() },
                            onTabClick = { cardsViewModel.onTabClick(it) },
                            onRecordClick = {
                                if (cardsViewModel.onRecordClick(it, has(Manifest.permission.RECORD_AUDIO))) {
                                    cardsMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onDeleteClick = { cardsViewModel.onDeleteClick(it) },
                            onDeleteAllClick = { cardsViewModel.onDeleteAllClick() },
                            onRevokeClick = { cardsViewModel.onRevokeClick() },
                            onCloseClick = { cardsViewModel.close() },
                        )
                        return@Surface
                    }
                    MainScreen(
                        state = viewModel.uiState.collectAsState().value,
                        onStartClick = {
                            if (viewModel.onStartClick(has(Manifest.permission.RECORD_AUDIO))) {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onLocationConsentClick = {
                            if (viewModel.onLocationConsentClick(has(Manifest.permission.ACCESS_FINE_LOCATION))) {
                                locationPermission.launch(LOCATION_PERMISSIONS)
                            }
                        },
                        onDifficultyClick = { viewModel.onDifficultyClick(it) },
                        onPauseClick = { viewModel.onPauseClick() },
                        onCardsClick = { cardsViewModel.onOpenClick() },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onScreenShown()
    }

    /**
     * 화면이 완전히 가려지면(홈·다른 앱) 이용을 끝낸다. 권한 창·화면 분할에서 다른 창 터치는 onPause까지만 와서 이탈이 아니고,
     * 화면 회전 같은 재생성은 같은 컨트롤러에 다시 연결된다.
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            cardsViewModel.close()
            viewModel.onScreenLeft()
        }
    }

    private fun has(permission: String): Boolean {
        val state = ContextCompat.checkSelfPermission(this, permission)
        return state == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        val LOCATION_PERMISSIONS =
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
