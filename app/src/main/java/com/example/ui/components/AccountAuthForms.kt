package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.OtpAuthValidator

enum class AuthFormMode {
  LOGIN,
  SIGN_UP,
  FORGOT_PASSWORD,
}

@Composable
fun LogInForm(
  email: String,
  onEmailChange: (String) -> Unit,
  password: String,
  onPasswordChange: (String) -> Unit,
  isBusy: Boolean,
  onSubmitLogIn: () -> Unit,
  onForgotPasswordClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    EmailTextField(
      value = email,
      onValueChange = onEmailChange,
      enabled = !isBusy,
      imeAction = ImeAction.Next,
      modifier = Modifier.testTag("login_email_input"),
    )

    PasswordTextField(
      value = password,
      onValueChange = onPasswordChange,
      label = stringResource(R.string.auth_label_password),
      placeholder = stringResource(R.string.auth_placeholder_password_login),
      enabled = !isBusy,
      imeAction = ImeAction.Done,
      onImeDone = onSubmitLogIn,
      modifier = Modifier.testTag("login_password_input"),
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End,
    ) {
      TextButton(
        onClick = onForgotPasswordClick,
        enabled = !isBusy,
        modifier = Modifier.heightIn(min = 48.dp).testTag("forgot_password_button"),
      ) {
        Text(
          text = stringResource(R.string.auth_action_forgot_password),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }

    Button(
      onClick = onSubmitLogIn,
      enabled = !isBusy,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("login_submit_button"),
    ) {
      if (isBusy) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
      }
      Text(
        text = stringResource(R.string.auth_action_login),
        style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

@Composable
fun CreateAccountForm(
  username: String,
  onUsernameChange: (String) -> Unit,
  email: String,
  onEmailChange: (String) -> Unit,
  password: String,
  onPasswordChange: (String) -> Unit,
  otp: String,
  onOtpChange: (String) -> Unit,
  otpSentToEmail: String?,
  otpCooldownSeconds: Int,
  isBusy: Boolean,
  isSendingOtp: Boolean,
  onSendOtp: () -> Unit,
  onSubmitCreateAccount: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    OutlinedTextField(
      value = username,
      onValueChange = { if (it.length <= 30) onUsernameChange(it) },
      label = { Text(stringResource(R.string.auth_label_username)) },
      placeholder = { Text(stringResource(R.string.auth_placeholder_username)) },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Person,
          contentDescription = null,
        )
      },
      singleLine = true,
      enabled = !isBusy,
      keyboardOptions =
        KeyboardOptions(
          capitalization = KeyboardCapitalization.Words,
          imeAction = ImeAction.Next,
        ),
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth().testTag("signup_username_input"),
    )

    EmailTextField(
      value = email,
      onValueChange = onEmailChange,
      enabled = !isBusy,
      imeAction = ImeAction.Next,
      modifier = Modifier.testTag("signup_email_input"),
    )

    PasswordTextField(
      value = password,
      onValueChange = onPasswordChange,
      label = stringResource(R.string.auth_label_password),
      placeholder = stringResource(R.string.auth_placeholder_password),
      enabled = !isBusy,
      imeAction = ImeAction.Next,
      modifier = Modifier.testTag("signup_password_input"),
    )

    EmailOtpVerificationCard(
      otp = otp,
      onOtpChange = onOtpChange,
      otpSentToEmail = otpSentToEmail,
      otpCooldownSeconds = otpCooldownSeconds,
      isBusy = isBusy,
      isSendingOtp = isSendingOtp,
      onSendOtp = onSendOtp,
      onOtpDone = onSubmitCreateAccount,
      sendButtonTestTag = "signup_send_otp_button",
      otpInputTestTag = "signup_otp_input",
    )

    Button(
      onClick = onSubmitCreateAccount,
      enabled = !isBusy,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("signup_submit_button"),
    ) {
      if (isBusy && !isSendingOtp) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
      }
      Text(
        text = stringResource(R.string.auth_action_create_account),
        style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

@Composable
fun ForgotPasswordForm(
  email: String,
  onEmailChange: (String) -> Unit,
  otp: String,
  onOtpChange: (String) -> Unit,
  newPassword: String,
  onNewPasswordChange: (String) -> Unit,
  otpSentToEmail: String?,
  otpCooldownSeconds: Int,
  isBusy: Boolean,
  isSendingOtp: Boolean,
  onSendResetOtp: () -> Unit,
  onSubmitResetPassword: () -> Unit,
  onBackToLogin: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(enabled = !isBusy) {
    onBackToLogin()
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(
        onClick = onBackToLogin,
        enabled = !isBusy,
        modifier = Modifier.heightIn(min = 48.dp).testTag("forgot_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.auth_action_back_to_login),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }

    Text(
      text = stringResource(R.string.auth_forgot_password_title),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Text(
      text = stringResource(R.string.auth_forgot_password_subtitle),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    EmailTextField(
      value = email,
      onValueChange = onEmailChange,
      enabled = !isBusy,
      imeAction = ImeAction.Next,
      modifier = Modifier.testTag("reset_email_input"),
    )

    EmailOtpVerificationCard(
      otp = otp,
      onOtpChange = onOtpChange,
      otpSentToEmail = otpSentToEmail,
      otpCooldownSeconds = otpCooldownSeconds,
      isBusy = isBusy,
      isSendingOtp = isSendingOtp,
      onSendOtp = onSendResetOtp,
      onOtpDone = {},
      sendButtonTestTag = "reset_send_otp_button",
      otpInputTestTag = "reset_otp_input",
      otpImeAction = ImeAction.Next,
    )

    PasswordTextField(
      value = newPassword,
      onValueChange = onNewPasswordChange,
      label = stringResource(R.string.auth_label_new_password),
      placeholder = stringResource(R.string.auth_placeholder_password),
      enabled = !isBusy,
      imeAction = ImeAction.Done,
      onImeDone = onSubmitResetPassword,
      modifier = Modifier.testTag("reset_password_input"),
    )

    Button(
      onClick = onSubmitResetPassword,
      enabled = !isBusy,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("reset_submit_button"),
    ) {
      if (isBusy && !isSendingOtp) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
      }
      Text(
        text = stringResource(R.string.auth_action_reset_password),
        style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

@Composable
private fun EmailTextField(
  value: String,
  onValueChange: (String) -> Unit,
  enabled: Boolean,
  imeAction: ImeAction,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(stringResource(R.string.auth_label_email)) },
    placeholder = { Text(stringResource(R.string.auth_placeholder_email)) },
    leadingIcon = {
      Icon(
        imageVector = Icons.Rounded.Email,
        contentDescription = null,
      )
    },
    singleLine = true,
    enabled = enabled,
    keyboardOptions =
      KeyboardOptions(
        keyboardType = KeyboardType.Email,
        imeAction = imeAction,
      ),
    shape = MaterialTheme.shapes.small,
    modifier = modifier.fillMaxWidth(),
  )
}

@Composable
private fun PasswordTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  placeholder: String,
  enabled: Boolean,
  imeAction: ImeAction,
  onImeDone: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var isPasswordVisible by remember { mutableStateOf(false) }

  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    placeholder = { Text(placeholder) },
    leadingIcon = {
      Icon(
        imageVector = Icons.Rounded.Lock,
        contentDescription = null,
      )
    },
    trailingIcon = {
      IconButton(
        onClick = { isPasswordVisible = !isPasswordVisible },
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector =
            if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
          contentDescription =
            stringResource(
              if (isPasswordVisible) R.string.cd_hide_password else R.string.cd_show_password
            ),
        )
      }
    },
    singleLine = true,
    enabled = enabled,
    visualTransformation =
      if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
    keyboardOptions =
      KeyboardOptions(
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
      ),
    keyboardActions = KeyboardActions(onDone = { onImeDone() }),
    shape = MaterialTheme.shapes.small,
    modifier = modifier.fillMaxWidth(),
  )
}

@Composable
private fun EmailOtpVerificationCard(
  otp: String,
  onOtpChange: (String) -> Unit,
  otpSentToEmail: String?,
  otpCooldownSeconds: Int,
  isBusy: Boolean,
  isSendingOtp: Boolean,
  onSendOtp: () -> Unit,
  onOtpDone: () -> Unit,
  sendButtonTestTag: String,
  otpInputTestTag: String,
  otpImeAction: ImeAction = ImeAction.Done,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f).padding(end = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.MarkEmailRead,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text =
              if (!otpSentToEmail.isNullOrBlank()) {
                stringResource(R.string.auth_otp_sent_short, otpSentToEmail)
              } else {
                stringResource(R.string.auth_otp_section_title)
              },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        FilledTonalButton(
          onClick = onSendOtp,
          enabled = !isBusy && otpCooldownSeconds == 0,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.heightIn(min = 48.dp).testTag(sendButtonTestTag),
        ) {
          if (isSendingOtp) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(6.dp))
          }
          Text(
            text =
              when {
                otpCooldownSeconds > 0 ->
                  stringResource(R.string.auth_action_resend_countdown, otpCooldownSeconds)
                !otpSentToEmail.isNullOrBlank() -> stringResource(R.string.auth_action_resend_otp)
                else -> stringResource(R.string.auth_action_send_otp)
              },
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }

      OutlinedTextField(
        value = otp,
        onValueChange = { onOtpChange(OtpAuthValidator.normalizeOtp(it)) },
        label = { Text(stringResource(R.string.auth_label_otp)) },
        placeholder = { Text(stringResource(R.string.auth_placeholder_otp)) },
        singleLine = true,
        enabled = !isBusy,
        keyboardOptions =
          KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = otpImeAction,
          ),
        keyboardActions = KeyboardActions(onDone = { onOtpDone() }),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(otpInputTestTag),
      )

      Text(
        text = stringResource(R.string.auth_otp_sender_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
