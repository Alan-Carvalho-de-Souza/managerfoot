package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.managerfoot.data.database.entities.Posicao
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.ForcaBadge
import br.com.managerfoot.presentation.ui.components.PosicaoBadge
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing

// ═══════════════════════════════════════════════════════════
//  Aposentadoria de jogador — fluxo compartilhado
//  Usado pela tela de Escalação e pela aba "Meu elenco" (Jogadores).
//  Ao aposentar, um junior do clube herda a vaga: o usuário escolhe a
//  POSIÇÃO dele e se ele leva o NOME do ídolo ou um nome novo.
// ═══════════════════════════════════════════════════════════

/** Faixa em que a aposentadoria manual é oferecida. */
internal fun podeAposentar(jogador: Jogador): Boolean =
    jogador.idade in 33..44 && !jogador.aposentado

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AposentadoriaDialog(
    jogador: Jogador,
    onConfirmar: (posicao: Posicao, manterNome: Boolean, novoNome: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var posicao    by remember { mutableStateOf(jogador.posicao) }
    var manterNome by remember { mutableStateOf(true) }
    var novoNome   by remember { mutableStateOf("") }
    var menuAberto by remember { mutableStateOf(false) }

    val nomeValido = manterNome || novoNome.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(jogador.posicao.abreviacao, jogador.posicao.setor)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Aposentar jogador",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${jogador.nome} · ${jogador.idade} anos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ForcaBadge(jogador.forca)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    "Um jogador da base assume a vaga no elenco. Escolha a posição " +
                    "dele e se ele levará o nome do ídolo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Posição do sucessor
                ExposedDropdownMenuBox(
                    expanded = menuAberto,
                    onExpandedChange = { menuAberto = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "${posicao.abreviacao} · ${posicao.name.replace("_", " ")}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Posição") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuAberto) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(Radius.md)
                    )
                    ExposedDropdownMenu(
                        expanded = menuAberto,
                        onDismissRequest = { menuAberto = false },
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        Posicao.entries.forEach { pos ->
                            DropdownMenuItem(
                                text = { Text("${pos.abreviacao} · ${pos.name.replace("_", " ")}") },
                                onClick = { posicao = pos; menuAberto = false }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Nome do sucessor
                OpcaoNome(
                    selecionada = manterNome,
                    titulo = "Manter o nome",
                    descricao = jogador.nome,
                    onClick = { manterNome = true }
                )
                OpcaoNome(
                    selecionada = !manterNome,
                    titulo = "Alterar o nome",
                    descricao = "Escolher um nome novo para a cria",
                    onClick = { manterNome = false }
                )
                if (!manterNome) {
                    OutlinedTextField(
                        value = novoNome,
                        onValueChange = { novoNome = it },
                        label = { Text("Nome do jogador") },
                        singleLine = true,
                        isError = novoNome.isBlank(),
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "Esta ação não pode ser desfeita.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmar(posicao, manterNome, novoNome.trim().takeIf { !manterNome && it.isNotEmpty() })
                },
                enabled = nomeValido,
                shape = RoundedCornerShape(Radius.sm),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Aposentar", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun OpcaoNome(
    selecionada: Boolean,
    titulo: String,
    descricao: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selecionada, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                descricao,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
