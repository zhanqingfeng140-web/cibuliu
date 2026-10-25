package com.wordecho.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordecho.app.R
import com.wordecho.app.data.Word

/**
 * 某一次上传的词表：先看清楚这批是什么词，再决定要不要复习。
 */
@Composable
fun SessionWordsScreen(
    title: String,
    subtitle: String,
    words: List<Word>,
    onBack: () -> Unit,
    onSpeak: (String) -> Unit,
    onStartReview: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    ScreenScaffold(title = title, subtitle = subtitle, onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {

            if (words.isEmpty()) {
                Text(
                    "这一批已经没有词了",
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant
                )
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(words, key = { _, w -> w.id }) { index, w ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 11.sp,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.width(22.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    w.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface
                                )
                                Text(
                                    w.meaning.ifBlank { "（还没有释义）" },
                                    fontSize = 11.sp,
                                    color = if (w.meaning.isBlank()) cs.error else cs.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onSpeak(w.text) }, modifier = Modifier.width(36.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_speaker),
                                    contentDescription = "朗读",
                                    tint = cs.primary,
                                    modifier = Modifier.width(20.dp).height(20.dp)
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(10.dp)) }
            }

            if (words.isNotEmpty()) {
                Button(
                    onClick = onStartReview,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("复习这一批（${words.size} 个）", fontSize = 15.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
