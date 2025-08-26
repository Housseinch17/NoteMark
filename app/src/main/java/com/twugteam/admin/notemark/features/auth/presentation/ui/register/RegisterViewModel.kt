package com.twugteam.admin.notemark.features.auth.presentation.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twugteam.admin.notemark.R
import com.twugteam.admin.notemark.core.domain.util.DataError
import com.twugteam.admin.notemark.core.domain.util.Result
import com.twugteam.admin.notemark.core.presentation.ui.UiText
import com.twugteam.admin.notemark.core.presentation.ui.asUiText
import com.twugteam.admin.notemark.features.auth.domain.AuthRepository
import com.twugteam.admin.notemark.features.auth.domain.UserDataValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class RegisterViewModel(
    private val userDataValidator: UserDataValidator,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<RegisterEvents>()
    val events = _events.receiveAsFlow()

    fun onAction(action: RegisterActions) {
            when (action) {
                is RegisterActions.UpdateUsername -> updateUsername(action.username)
                is RegisterActions.UpdateEmail -> updateEmail(action.email)
                is RegisterActions.UpdatePassword -> updatePassword(action.password)
                is RegisterActions.UpdateConfirmPassword -> updateConfirmPassword(action.confirmPassword)
                RegisterActions.OnRegisterClick -> register()
                RegisterActions.OnAlreadyHaveAnAccountClick -> navigateToLogin()
        }
    }

    private fun updateUsername(username: String) {
        val validityState = userDataValidator.isUsernameValid(username)
        _state.update {
            it.copy(
                username = it.username.copy(
                    value = username,
                    isValid = validityState.isValid,
                    isError = if (username.isNotBlank()) !validityState.isValid else false,
                    errorText = validityState.errorText
                )
            )
        }
        val canRegister = canRegister()
        _state.update { newState ->
            newState.copy(canRegister = canRegister)
        }
    }

    private fun updateEmail(email: String) {
        val isValid = userDataValidator.isValidEmail(email)
        _state.update {
            it.copy(
                email = it.email.copy(
                    value = email,
                    isValid = isValid,
                    isError = if (email.isNotBlank()) !isValid else false,
                ),
            )
        }
        val canRegister = canRegister()
        _state.update { newState ->
            newState.copy(canRegister = canRegister)
        }
    }

    private fun updatePassword(password: String) {
        val isValid = userDataValidator.validatePassword(password)
        val isConfirmPasswordValid = userDataValidator.isValidConfirmPassword(
            password = password,
            confirmPassword = _state.value.confirmPassword.value
        )
        _state.update {
            it.copy(
                password = it.password.copy(
                    value = password,
                    isValid = isValid,
                    isError = !isValid,
                ),

            )
        }

        val isConfirmPasswordEmpty = _state.value.confirmPassword.value.isEmpty()
        //if confirm password not empty update confirm password error
        if(!isConfirmPasswordEmpty){
            _state.update { newState->
                newState.copy(
                    confirmPassword = newState.confirmPassword.copy(
                        isError = !isConfirmPasswordValid
                    )
                )
            }
        }
        val canRegister = canRegister()
        _state.update { newState ->
            newState.copy(canRegister = canRegister)
        }
        Timber.tag("MyTag").d("updatePassword: isValid: $isValid isConfirm: $isConfirmPasswordValid")
    }

    private fun updateConfirmPassword(confirmPassword: String) {
        val isValid = userDataValidator.isValidConfirmPassword(
            password = _state.value.password.value,
            confirmPassword = confirmPassword
        )
        _state.update {
            it.copy(
                confirmPassword = it.confirmPassword.copy(
                    value = confirmPassword,
                    isValid = isValid,
                    isError = !isValid
                ),
            )
        }


        val canRegister = canRegister()
        _state.update { newState ->
            newState.copy(canRegister = canRegister)
        }
        Timber.tag("MyTag").d("confirmPassword isValid: $isValid")
    }

    private fun register() {
        viewModelScope.launch {
            _state.update { it.copy(isRegistering = true) }
            val result = authRepository.register(
                username = _state.value.username.value,
                email = _state.value.email.value,
                password = _state.value.password.value
            )

            when (result) {
                is Result.Error -> {
                    if (result.error == DataError.Network.CONFLICT) {
                        _events.send(
                            RegisterEvents.RegistrationError(
                                error = UiText.StringResource(
                                    R.string.error_conflict
                                )
                            )
                        )
                    } else {
                        _events.send(RegisterEvents.RegistrationError(error = result.error.asUiText()))
                    }
                    _state.update { it.copy(isRegistering = false) }
                }

                is Result.Success -> {
                    //clear state
                    resetState()
                    _events.send(RegisterEvents.RegistrationSuccess)
                }
            }
        }
    }


    private fun canRegister(): Boolean {
        val state = _state.value
        return state.username.isValid && state.email.isValid && state.password.isValid && (state.password.value == state.confirmPassword.value)
    }

    private  fun navigateToLogin() {
        viewModelScope.launch {
            _events.send(RegisterEvents.NavigateToLogin)
        }
    }

    fun showSnackBar(errorMessage: UiText) {
        viewModelScope.launch {
            _state.update { newState ->
                newState.copy(
                    showSnackBar = true,
                    snackBarText = errorMessage
                )
            }
            //show snackBar for 2 seconds
            delay(2.seconds)
            _state.update { newState ->
                newState.copy(
                    showSnackBar = false,
                    snackBarText = UiText.DynamicString("")
                )
            }
        }
    }

    private fun resetState() {
        _state.value = RegisterUiState()
    }
}
