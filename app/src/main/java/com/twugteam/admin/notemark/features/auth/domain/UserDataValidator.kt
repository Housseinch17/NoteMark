package com.twugteam.admin.notemark.features.auth.domain

import com.twugteam.admin.notemark.core.presentation.ui.UiText
import com.twugteam.admin.notemark.R

class UserDataValidator(
    private val patternValidator: PatternValidator
) {
    companion object {
        const val MIN_USERNAME_LENGTH = 3
        const val MAX_USERNAME_LENGTH = 20
        const val MIN_PASSWORD_LENGTH = 8
    }

    fun isValidEmail(email: String): Boolean {
        return patternValidator.matches(email)
    }

    fun validatePassword(password: String): Boolean {
        val hasMinimumLength = password.length >= MIN_PASSWORD_LENGTH
        val hasNumber = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isDigit() && !it.isLetter() }

        return (hasMinimumLength && (hasNumber && hasSymbol))
    }

    data class ValidityState(
        val isValid: Boolean,
        val errorText: UiText
    )

    fun isUsernameValid(username: String): ValidityState {
        val isValid =
            username.length >= MIN_USERNAME_LENGTH && username.length <= MAX_USERNAME_LENGTH
        return if (isValid) {
            ValidityState(
                isValid = true,
                errorText = UiText.DynamicString("")
            )
        } else {
            val errorText = UiText.StringResource(R.string.username_info,3,20)
            ValidityState(
                isValid = false,
                errorText = errorText
            )
        }
    }


    fun isValidConfirmPassword(password: String, confirmPassword: String): Boolean {
        val bothPasswordFieldsAreNotEmpty = password.isNotEmpty() && confirmPassword.isNotEmpty()
        val isConfirmValid = validatePassword(password = confirmPassword)
        return bothPasswordFieldsAreNotEmpty && password == confirmPassword && isConfirmValid
    }
}