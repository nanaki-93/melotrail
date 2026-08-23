package app.melotrail.desktop

import app.melotrail.application.WorkflowAction
import app.melotrail.application.WorkflowApprovalState
import app.melotrail.application.WorkflowPrerequisite
import app.melotrail.application.WorkflowStage
import app.melotrail.application.WorkflowStageStatus
import app.melotrail.application.WorkflowStep
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowPresentationTest {
    @Test
    fun `desktop status labels are driven by the normalized workflow lifecycle`() {
        val failed = step(WorkflowStageStatus.FAILED)
        val approved = step(WorkflowStageStatus.APPROVED, WorkflowApprovalState.APPROVED)

        assertEquals("Failed", workflowStatusLabel(failed))
        assertEquals("Approved", workflowStatusLabel(approved))
    }

    private fun step(status: WorkflowStageStatus, approval: WorkflowApprovalState = WorkflowApprovalState.NOT_REQUIRED) = WorkflowStep(
        stage = WorkflowStage.ARRANGEMENT,
        state = app.melotrail.application.WorkflowState.CURRENT,
        nextAction = WorkflowAction.GENERATE_ARRANGEMENT,
        prerequisite = WorkflowPrerequisite.APPROVED_ARRANGEMENT,
        status = status,
        approval = approval
    )
}
