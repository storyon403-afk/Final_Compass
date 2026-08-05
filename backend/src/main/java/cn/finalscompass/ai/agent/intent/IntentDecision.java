package cn.finalscompass.ai.agent.intent;


public record IntentDecision(

        IntentType type,


        String subject,


        Difficulty difficulty,


        double confidence,


        boolean needImageParser,


        String reason

) {


}