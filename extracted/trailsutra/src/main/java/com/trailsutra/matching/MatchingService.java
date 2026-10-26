package com.trailsutra.matching;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service
public class MatchingService {
    private final double referenceWeight, amountWeight, victimWeight, timeWeight, highConfidence, margin, manualReview; private final long windowMinutes;
    public MatchingService(@Value("${trailsutra.matching.weights.reference}") double rw,@Value("${trailsutra.matching.weights.amount}") double aw,@Value("${trailsutra.matching.weights.victim}") double vw,@Value("${trailsutra.matching.weights.time}") double tw,@Value("${trailsutra.matching.high-confidence}") double hc,@Value("${trailsutra.matching.margin}") double margin,@Value("${trailsutra.matching.manual-review}") double mr,@Value("${trailsutra.matching.time-window-minutes}") long wm){this.referenceWeight=rw;this.amountWeight=aw;this.victimWeight=vw;this.timeWeight=tw;this.highConfidence=hc;this.margin=margin;this.manualReview=mr;this.windowMinutes=wm;}
    public Candidate score(boolean referenceMatch, long complaintPaise, long transactionPaise, boolean victimMatch, Instant incident, Instant transaction){double ref=referenceMatch?1:0;double amount=complaintPaise==transactionPaise?1:0;double victim=victimMatch?1:0;double delta=Math.abs(Duration.between(incident,transaction).toSeconds())/60.0;double time=1-Math.min(1,delta/windowMinutes);double score=referenceWeight*ref+amountWeight*amount+victimWeight*victim+timeWeight*time;return new Candidate(score,ref==1&&amount==1&&victim==1&&time>0,score>=highConfidence?"HIGH_CONFIDENCE":score>=manualReview?"MANUAL_REVIEW":"UNRESOLVED");}
    public boolean hasMargin(double first,double second){return first>=highConfidence&&first-second>=margin;}
    public record Candidate(double score,boolean verifiedReferenceCandidate,String disposition){}
}
