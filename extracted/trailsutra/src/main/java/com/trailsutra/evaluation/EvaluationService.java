package com.trailsutra.evaluation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class EvaluationService {
    private final JdbcTemplate jdbc;
    public EvaluationService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Metrics evaluate(String caseId,double threshold){var counts=jdbc.queryForMap("SELECT SUM(CASE WHEN g.expected_positive=TRUE AND s.priority_score>=? THEN 1 ELSE 0 END) tp,SUM(CASE WHEN g.expected_positive=FALSE AND s.priority_score>=? THEN 1 ELSE 0 END) fp,SUM(CASE WHEN g.expected_positive=TRUE AND s.priority_score<? THEN 1 ELSE 0 END) fn,SUM(CASE WHEN g.expected_positive=FALSE AND s.priority_score<? THEN 1 ELSE 0 END) tn FROM ground_truth g JOIN account_scores s ON s.case_id=g.case_id AND s.account_token=g.account_token WHERE g.case_id=?",threshold,threshold,threshold,threshold,caseId);double tp=num(counts,"TP"),fp=num(counts,"FP"),fn=num(counts,"FN"),tn=num(counts,"TN");double precision=divide(tp,tp+fp),recall=divide(tp,tp+fn),f1=divide(2*precision*recall,precision+recall),fpr=divide(fp,fp+tn);return new Metrics(precision,recall,f1,fpr,tp,fp,fn,tn);}
    private double num(Map<String,Object> m,String key){Object v=m.get(key);return v==null?0:((Number)v).doubleValue();}private double divide(double a,double b){return b==0?0:a/b;}
    public record Metrics(double precision,double recall,double f1,double falsePositiveRate,double truePositive,double falsePositive,double falseNegative,double trueNegative){}
}
