package com.ramanan.fit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramanan.fit.R

/** First launch (and "Change name"): the coach asks for the user's name. */
@Composable
fun NameScreen(state: UiState, vm: WorkoutViewModel) {
    var text by rememberSaveable { mutableStateOf(state.name) }
    val canSave = text.isNotBlank()
    val editing = state.name.isNotBlank()
    BackHandler(enabled = editing) { vm.cancelEditName() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.coach_photo),
            contentDescription = S.coachTitle,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.size(160.dp).clip(CircleShape).border(3.dp, Palette.Exercise, CircleShape),
        )
        Spacer(Modifier.height(20.dp))
        Text(S.nameTitle, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(S.nameQuestion, color = Palette.Exercise, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(S.nameWhy, color = Palette.TextDim, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(24) },
            singleLine = true,
            placeholder = { Text(S.nameHint) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) vm.saveName(text) }),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, color = Palette.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Exercise, unfocusedBorderColor = Palette.SurfaceHi,
                cursorColor = Palette.Exercise,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.saveName(text) },
            enabled = canSave,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Exercise, contentColor = Color(0xFF00210F)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) { Text(S.nameContinue, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (editing) {
            TextButton(onClick = vm::cancelEditName, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(S.cancel, color = Palette.TextDim)
            }
        }
    }
}
