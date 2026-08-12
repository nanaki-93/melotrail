# Task 004 — Structure Parser and Timeline

## Goal
Convert structure text into explicit ordered section instances.

## Agent prompt
Implement a simple parser for:

`A A B B A C B`

and optionally:

`A*2 B*2 A C*2`

Output explicit section instances with stable indexes.

Requirements:
- normalize whitespace;
- validate part IDs;
- preserve order;
- tests for valid, invalid, empty, repeated input.

Do not build a music-notation parser or AI structure inference.
