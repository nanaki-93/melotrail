#!/usr/bin/env python3
"""Validate the anonymized MC-048I observed-arrangement-session evidence."""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from collections import Counter
from datetime import date
from pathlib import Path
from typing import Any


TEMPLATE = {
    "sessions": [
        {
            "sessionId": "UX-01",
            "date": "YYYY-MM-DD",
            "participantRole": "musician-non-implementer",
            "projectAlias": "license-safe-project-alias",
            "projectSha256": "64 lowercase hexadecimal characters",
            "authorityComplete": True,
            "primaryActionsToFirstDraftListen": 3,
            "timeToFirstSoundMs": 0,
            "previewOnsetMs": 0,
            "timeToFirstDraftListenMs": 0,
            "draftProgressObserved": True,
            "draftCancellationObserved": True,
            "playerVisibleAfterScrolling": True,
            "navigationContinuityObserved": True,
            "previewAndCancellationImmutable": True,
            "advancedControlsRequired": False,
            "abandonedActions": 0,
            "wrongScopeRegenerations": 0,
            "activeSectionAndTargetExplained": True,
            "confusions": []
        }
    ]
}

REQUIRED_BOOLEAN_FIELDS = (
    "authorityComplete",
    "draftProgressObserved",
    "draftCancellationObserved",
    "playerVisibleAfterScrolling",
    "navigationContinuityObserved",
    "previewAndCancellationImmutable",
    "advancedControlsRequired",
    "activeSectionAndTargetExplained",
)
TIMING_FIELDS = ("timeToFirstSoundMs", "previewOnsetMs", "timeToFirstDraftListenMs")
COUNT_FIELDS = ("primaryActionsToFirstDraftListen", "abandonedActions", "wrongScopeRegenerations")
NON_IMPLEMENTER = "musician-non-implementer"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, help="Completed anonymized session JSON.")
    parser.add_argument("--template", action="store_true", help="Print an evidence template; never evidence itself.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.template:
        if args.input:
            print("Use either --template or --input, not both.", file=sys.stderr)
            return 2
        print(json.dumps(TEMPLATE, indent=2))
        return 0
    if not args.input:
        print("--input is required unless --template is used.", file=sys.stderr)
        return 2
    try:
        payload = json.loads(args.input.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"Cannot read session evidence: {error}", file=sys.stderr)
        return 2
    errors, summary = validate(payload)
    print(json.dumps(summary, indent=2, sort_keys=True))
    if errors:
        print("MC-048I UX evidence does not pass:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("MC-048I UX evidence passes the recorded-session gate.")
    return 0


def validate(payload: Any) -> tuple[list[str], dict[str, Any]]:
    errors: list[str] = []
    if not isinstance(payload, dict) or not isinstance(payload.get("sessions"), list):
        return ["Root must be an object containing a sessions array."], {"status": "invalid"}
    sessions = payload["sessions"]
    if len(sessions) < 5:
        errors.append("At least five observed sessions are required.")
    seen_ids: set[str] = set()
    normalized_confusions: list[str] = []
    first_draft_times: list[int] = []
    first_sound_times: list[int] = []
    preview_onsets: list[int] = []
    totals = {field: 0 for field in COUNT_FIELDS}
    non_implementers = 0
    cancellation_observed = False
    for index, session in enumerate(sessions, start=1):
        label = f"sessions[{index}]"
        if not isinstance(session, dict):
            errors.append(f"{label} must be an object.")
            continue
        session_id = session.get("sessionId")
        if not isinstance(session_id, str) or not session_id.strip():
            errors.append(f"{label}.sessionId must be a non-empty anonymous ID.")
        elif session_id in seen_ids:
            errors.append(f"{label}.sessionId duplicates {session_id!r}.")
        else:
            seen_ids.add(session_id)
        if not isinstance(session.get("date"), str):
            errors.append(f"{label}.date must be ISO YYYY-MM-DD.")
        else:
            try:
                date.fromisoformat(session["date"])
            except ValueError:
                errors.append(f"{label}.date must be ISO YYYY-MM-DD.")
        if session.get("participantRole") == NON_IMPLEMENTER:
            non_implementers += 1
        elif session.get("participantRole") not in {"musician-implementer", "non-musician-observer"}:
            errors.append(f"{label}.participantRole must state musician/non-implementer status.")
        project_hash = session.get("projectSha256")
        if not isinstance(session.get("projectAlias"), str) or not session["projectAlias"].strip():
            errors.append(f"{label}.projectAlias is required; do not record a participant identity.")
        if not isinstance(project_hash, str) or len(project_hash) != 64 or any(char not in "0123456789abcdef" for char in project_hash):
            errors.append(f"{label}.projectSha256 must be a lowercase SHA-256 value.")
        for field in REQUIRED_BOOLEAN_FIELDS:
            if not isinstance(session.get(field), bool):
                errors.append(f"{label}.{field} must be true or false.")
        if session.get("authorityComplete") is not True:
            errors.append(f"{label} did not use an authority-complete project.")
        if session.get("advancedControlsRequired") is True:
            errors.append(f"{label} required advanced profile/pattern controls to reach the first draft.")
        if session.get("draftCancellationObserved") is True:
            cancellation_observed = True
        for field in ("draftProgressObserved", "playerVisibleAfterScrolling", "navigationContinuityObserved", "previewAndCancellationImmutable"):
            if session.get(field) is False:
                errors.append(f"{label}.{field} was not observed; fix the reproducible product failure and retest.")
        for field in TIMING_FIELDS:
            value = session.get(field)
            if not isinstance(value, int) or value < 0:
                errors.append(f"{label}.{field} must be a non-negative millisecond integer.")
            else:
                if field == "timeToFirstDraftListenMs":
                    first_draft_times.append(value)
                elif field == "timeToFirstSoundMs":
                    first_sound_times.append(value)
                else:
                    preview_onsets.append(value)
        for field in COUNT_FIELDS:
            value = session.get(field)
            if not isinstance(value, int) or value < 0:
                errors.append(f"{label}.{field} must be a non-negative integer.")
            else:
                totals[field] += value
        action_count = session.get("primaryActionsToFirstDraftListen")
        if isinstance(action_count, int) and action_count > 3:
            errors.append(f"{label} took {action_count} primary actions to first-draft listening; maximum is three.")
        confusions = session.get("confusions")
        if not isinstance(confusions, list) or not all(isinstance(item, str) and item.strip() for item in confusions):
            errors.append(f"{label}.confusions must be a list of concise non-empty observations.")
        else:
            normalized_confusions.extend(item.strip().casefold() for item in confusions)
    if non_implementers < 3:
        errors.append("At least three sessions must be musicians who did not implement the feature.")
    if sessions and not cancellation_observed:
        errors.append("At least one observed session must exercise complete-draft cancellation/retry.")
    if first_draft_times and statistics.median(first_draft_times) > 120_000:
        errors.append(f"Median time to first complete-draft listen is {statistics.median(first_draft_times)}ms; maximum is 120000ms.")
    repeated_confusions = sorted(item for item, count in Counter(normalized_confusions).items() if count >= 2)
    if repeated_confusions:
        errors.append("Repeated participant confusion requires a product fix and retest: " + "; ".join(repeated_confusions))
    summary = {
        "status": "pass" if not errors else "fail",
        "sessionCount": len(sessions),
        "musicianNonImplementerCount": non_implementers,
        "medianTimeToFirstDraftListenMs": statistics.median(first_draft_times) if first_draft_times else None,
        "medianTimeToFirstSoundMs": statistics.median(first_sound_times) if first_sound_times else None,
        "p95ObservedPreviewOnsetMs": percentile_95(preview_onsets),
        "totals": totals,
        "repeatedConfusions": repeated_confusions,
    }
    return errors, summary


def percentile_95(samples: list[int]) -> int | None:
    if not samples:
        return None
    ordered = sorted(samples)
    return ordered[(len(ordered) * 95 + 99) // 100 - 1]


if __name__ == "__main__":
    raise SystemExit(main())
