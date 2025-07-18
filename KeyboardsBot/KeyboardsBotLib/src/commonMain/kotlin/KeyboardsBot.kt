import dev.inmo.micro_utils.coroutines.subscribeLoggingDropExceptions
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.edit.edit
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.*
import dev.inmo.tgbotapi.extensions.utils.types.buttons.*
import dev.inmo.tgbotapi.extensions.utils.withContent
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.InlineQueryResultArticle
import dev.inmo.tgbotapi.types.InlineQueries.InputMessageContent.InputTextMessageContent
import dev.inmo.tgbotapi.types.InlineQueryId
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.utils.PreviewFeature
import dev.inmo.tgbotapi.utils.botCommand
import dev.inmo.tgbotapi.utils.regular
import dev.inmo.tgbotapi.utils.row
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext

fun String.parsePageAndCount(): Pair<Int, Int>? {
    val (pageString, countString) = split(" ").takeIf { it.count() > 1 } ?: return null
    return Pair(
        pageString.toIntOrNull() ?: return null,
        countString.toIntOrNull() ?: return null
    )
}

fun InlineKeyboardBuilder.includePageButtons(page: Int, count: Int) {
    val numericButtons = listOfNotNull(
        page - 1,
        page,
        page + 1,
    )
    row {
        val numbersRange = 1 .. count
        numericButtons.forEach {
            if (it in numbersRange) {
                dataButton(it.toString(), "$it $count")
            }
        }
    }
    row {
        copyTextButton("Command copy button", "/inline $page $count")
    }

    row {
        if (page - 1 > 2) {
            dataButton("<<", "1 $count")
        }
        if (page - 1 > 1) {
            dataButton("<", "${page - 2} $count")
        }

        if (page + 1 < count) {
            dataButton(">", "${page + 2} $count")
        }
        if (page + 2 < count) {
            dataButton(">>", "$count $count")
        }
    }
    row {
        inlineQueryInChosenChatButton(
            "Send somebody page",
            query = "$page $count",
            allowUsers = true,
            allowBots = true,
            allowGroups = true,
            allowChannels = true,
        )
    }
}

@OptIn(PreviewFeature::class)
suspend fun activateKeyboardsBot(
    token: String,
    print: (Any) -> Unit
) {
    val bot = telegramBot(token)

    print(bot.getMe())

    bot.buildBehaviourWithLongPolling(CoroutineScope(currentCoroutineContext() + SupervisorJob())) {
        onCommandWithArgs("inline") { message, args ->
            val numberArgs = args.mapNotNull { it.toIntOrNull() }
            val numberOfPages = numberArgs.getOrNull(1) ?: numberArgs.firstOrNull() ?: 10
            val page = numberArgs.firstOrNull()?.takeIf { numberArgs.size > 1 }?.coerceAtLeast(1) ?: 1
            reply(
                message,
                replyMarkup = inlineKeyboard {
                    includePageButtons(page, numberOfPages)
                }
            ) {
                regular("Your inline keyboard with $numberOfPages pages")
            }
        }

        onMessageDataCallbackQuery {
            val (page, count) = it.data.parsePageAndCount() ?: it.let {
                answer(it, "Unsupported data :(")
                return@onMessageDataCallbackQuery
            }

            edit(
                it.message.withContent<TextContent>() ?: it.let {
                    answer(it, "Unsupported message type :(")
                    return@onMessageDataCallbackQuery
                },
                replyMarkup = inlineKeyboard {
                    includePageButtons(page, count)
                }
            ) {
                regular("This is $page of $count")
            }
            answer(it)
        }
        onInlineMessageIdDataCallbackQuery {
            val (page, count) = it.data.parsePageAndCount() ?: it.let {
                answer(it, "Unsupported data :(")
                return@onInlineMessageIdDataCallbackQuery
            }

            editMessageText(
                it.inlineMessageId,
                replyMarkup = inlineKeyboard {
                    includePageButtons(page, count)
                }
            ) {
                regular("This is $page of $count")
            }
            answer(it)
        }

        onBaseInlineQuery {
            val page = it.query.takeWhile { it.isDigit() }.toIntOrNull() ?: return@onBaseInlineQuery
            val count = it.query.removePrefix(page.toString()).dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }
                .toIntOrNull() ?: return@onBaseInlineQuery

            answer(
                it,
                results = listOf(
                    InlineQueryResultArticle(
                        InlineQueryId(it.query),
                        "Send buttons",
                        InputTextMessageContent("It is sent via inline mode inline buttons"),
                        replyMarkup = inlineKeyboard {
                            includePageButtons(page, count)
                        }
                    )
                )
            )
        }

        onUnhandledCommand {
            reply(
                it,
                replyMarkup = replyKeyboard(resizeKeyboard = true, oneTimeKeyboard = true) {
                    row {
                        simpleButton("/inline")
                    }
                }
            ) {
                +"Use " + botCommand("inline") + " to get pagination inline keyboard"
            }
        }

        setMyCommands(BotCommand("inline", "Creates message with pagination inline keyboard"))

        allUpdatesFlow.subscribeLoggingDropExceptions(scope = this) {
            println(it)
        }
    }.join()
}
