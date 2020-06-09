package com.so_mini.tcc;

import org.aspectj.lang.ProceedingJoinPoint;

import com.so_mini.tcc.common.TccPhaseEnum;

public interface CCFailListener {

	void notify(ProceedingJoinPoint pjp,TccPhaseEnum phase);
}
