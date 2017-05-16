/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.stats.events.completion

data class EventLine(val event: LogEvent?,
                     val unknownLogEventFields: Set<String>,
                     val absentLogEventFields: Set<String>,
                     val originalLine: String) {

    constructor(event: DeserializedLogEvent, originalLine: String): this(event.event, event.unknownEventFields, event.absentEventFields, originalLine)

    val sessionUid: String?
        get() = event?.sessionUid

    val isOk: Boolean
        get() = event != null && unknownLogEventFields.isEmpty() && absentLogEventFields.isEmpty()
}

open class SessionsFilter {

    private val output = mutableListOf<String>()
    private val error = mutableListOf<String>()

    val outputLines: List<String>
        get() = output

    val errorLines: List<String>
        get() = error


    fun filter(input: Iterable<String>) {
        var currentSession: String? = null

        val session = mutableListOf<EventLine>()

        for (line in input) {
            if (line.trim().isEmpty()) continue

            val event = LogEventSerializer.fromString(line)
            val eventLine = EventLine(event, line)

            if (eventLine.sessionUid == currentSession) {
                session.add(eventLine)
            }
            else {
                processCompletionSession(session)
                session.clear()
                currentSession = eventLine.sessionUid
                session.add(eventLine)
            }
        }

        processCompletionSession(session)
    }


    private fun processCompletionSession(session: List<EventLine>) {
        if (session.isEmpty()) return
        if (session.any { !it.isOk }) {
            dumpSession(session, isValidSession = false)
            return
        }

        var isValidSession = false
        val initial = session.first()
        if (initial.event is CompletionStartedEvent) {
            val state = CompletionValidationState(initial.event)
            session.drop(1).forEach { state.accept(it.event!!) }
            isValidSession = state.isFinished && state.isValid
        }

        dumpSession(session, isValidSession)
    }

    open protected fun dumpSession(session: List<EventLine>, isValidSession: Boolean) {
        val writer = if (isValidSession) output else error
        session.forEach {
            writer.add(it.originalLine)
        }
    }

}


class CompletionValidationState(event: CompletionStartedEvent) : LogEventVisitor() {
    val allCompletionItemIds: MutableList<Int> = event.newCompletionListItems.map { it.id }.toMutableList()
    
    var currentPosition    = event.currentPosition
    var completionList     = event.completionListIds
    var currentId          = getSafeCurrentId(completionList, currentPosition)

    var isValid = true
    var isFinished = false

    private fun updateState(nextEvent: LookupStateLogData) {
        currentPosition = nextEvent.currentPosition
        allCompletionItemIds.addAll(nextEvent.newCompletionListItems.map { it.id })
        if (nextEvent.completionListIds.isNotEmpty()) {
            completionList = nextEvent.completionListIds
        }
        currentId = getSafeCurrentId(completionList, currentPosition)
    }

    private fun getSafeCurrentId(completionList: List<Int>, position: Int): Int {
        if (completionList.isEmpty()) {
            return -1
        }
        else if (position < completionList.size && position >= 0) {
            return completionList[position]
        }
        else {
            isValid = false
            return -2
        }
    }

    fun accept(nextEvent: LogEvent) {
        if (isFinished) {
            isValid = false            
        }
        else if (isValid) {
            nextEvent.accept(this)
        }
    }
    
    override fun visit(event: DownPressedEvent) {
        val beforeDownPressedPosition = currentPosition
        updateState(event)
        updateValid((beforeDownPressedPosition + 1) % completionList.size == currentPosition)
    }

    private fun updateValid(value: Boolean) {
        isValid = isValid && value
    }

    override fun visit(event: UpPressedEvent) {
        val beforeUpPressedPosition = currentPosition
        updateState(event)
        
        updateValid((completionList.size + beforeUpPressedPosition - 1) % completionList.size == currentPosition)
    }

    override fun visit(event: TypeEvent) {
        val newIds = event.newCompletionIds()
        val allIds = (completionList + newIds).toSet()
        
        updateValid(allIds.containsAll(event.completionListIds))
        
        updateState(event)
        updateValid(allCompletionItemIds.containsAll(completionList))
    }

    override fun visit(event: BackspaceEvent) {
        updateState(event)
        updateValid(allCompletionItemIds.containsAll(completionList))
    }

    override fun visit(event: ExplicitSelectEvent) {
        val selectedIdBefore = currentId
        updateState(event)

        updateValid(selectedIdBefore == currentId && allCompletionItemIds.find { it == currentId } != null)
        isFinished = true
    }

    override fun visit(event: CompletionCancelledEvent) {
        isFinished = true
    }

    override fun visit(event: TypedSelectEvent) {
        val id = event.selectedId
        updateValid(completionList[currentPosition] == id)
        isFinished = true
    }
    
}