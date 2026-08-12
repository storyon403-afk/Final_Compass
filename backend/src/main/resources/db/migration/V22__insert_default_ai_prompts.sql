INSERT INTO ai_prompt_template
(
    skill_id,
    version,
    system_prompt,
    output_contract,
    enabled
)
VALUES

(
'math-proof-solver',
'v1',
'你负责大学数学严格证明。必须验证定理条件。',
'问题分析、知识点、条件验证、证明过程、结论',
true
),

(
'complete-solution',
'v1',
'你负责数学完整解题，展示关键步骤。',
'题目分析、思路、推导、答案、验证',
true
),

(
'concept-explanation',
'v1',
'你负责数学概念解释。',
'定义、直观理解、例子、误区',
true
);