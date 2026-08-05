package cn.finalscompass.ai.agent.intent;


import org.springframework.stereotype.Component;


@Component
public class IntentValidator {


    private static final double MIN_CONFIDENCE = 0.5;



    public IntentDecision validate(
            IntentDecision decision
    ){


        if(decision == null){

            return fallback(
                    "EMPTY_DECISION"
            );

        }



        if (decision.type() == null || decision.difficulty() == null) {

            return fallback(
                    "MISSING_TYPE"
            );

        }



        if (!Double.isFinite(decision.confidence())
                || decision.confidence() < 0
                || decision.confidence() > 1){


            return fallback(
                    "INVALID_CONFIDENCE"
            );

        }




        if (decision.type() != IntentType.UNKNOWN
                && decision.confidence() < MIN_CONFIDENCE){


            return fallback(
                    "LOW_CONFIDENCE"
            );

        }



        return decision;

    }





    private IntentDecision fallback(
            String reason
    ){

        return new IntentDecision(

                IntentType.UNKNOWN,

                "unknown",

                Difficulty.BASIC,

                0.0,

                false,

                reason

        );

    }

}
