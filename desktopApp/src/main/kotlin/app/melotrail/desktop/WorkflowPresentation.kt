package app.melotrail.desktop

import app.melotrail.application.WorkflowPrerequisite
import app.melotrail.application.WorkflowState
import app.melotrail.application.WorkflowStep

/** Desktop copy for the typed workflow model; the application model contains no UI strings. */
internal fun workflowDescription(step: WorkflowStep): String {
    if (step.state == WorkflowState.COMPLETE) return "Current artifact validated."
    val target = step.partId?.let { " for $it" }.orEmpty()
    return when (step.prerequisite) {
        WorkflowPrerequisite.NONE -> "This optional review is available."
        WorkflowPrerequisite.PROJECT_ROOT -> "Create or open a project."
        WorkflowPrerequisite.SCHEMA_V3 -> "Explicitly migrate the readable v2 project before continuing."
        WorkflowPrerequisite.IMPORTED_SOURCE -> "Import a MIDI or eligible solo-piano audio source$target."
        WorkflowPrerequisite.SOURCE_INSPECTION -> "Inspect the preserved source$target."
        WorkflowPrerequisite.RAW_MIDI -> "Publish immutable raw MIDI$target."
        WorkflowPrerequisite.CLEANED_MIDI -> "Create current cleaned MIDI and quality evidence$target."
        WorkflowPrerequisite.CLEAN_MIDI_APPROVAL -> "Review and approve the cleaned-MIDI quality evidence$target."
        WorkflowPrerequisite.APPROVED_AI_FIX -> "Keep cleaned MIDI or approve a current AI fix$target."
        WorkflowPrerequisite.SELECTED_MIDI -> "Select a current MIDI Feel input$target."
        WorkflowPrerequisite.CURRENT_ANALYSIS -> "Analyze the current selected MIDI$target."
        WorkflowPrerequisite.SAVED_STRUCTURE -> "Save a structure using current part IDs."
        WorkflowPrerequisite.APPROVED_COHESION -> "Generate and approve current Cohesion."
        WorkflowPrerequisite.APPROVED_ARRANGEMENT -> "Generate and approve a current arrangement."
        WorkflowPrerequisite.RENDERED_STEMS -> "Render current stems from the approved arrangement."
        WorkflowPrerequisite.DRY_MIX -> "Create the current dry mix."
        WorkflowPrerequisite.MASTER -> "Create and validate the current master and release metadata."
        WorkflowPrerequisite.SOURCE_RIGHTS_ATTESTATION -> "Review source-rights attestations for commercial export."
    }
}
