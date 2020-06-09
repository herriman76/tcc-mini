package com.so_mini.tcc.aspectj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.so_mini.tcc.CCFailListener;
import com.so_mini.tcc.common.TccPhaseEnum;

@Aspect//来定义一个切面
@Component
public class TccAspectj {
	
	@Autowired(required=false)
	private List<CCFailListener> _CCFailListenerList;
	
	private static final ThreadLocal<Object> threadLocal = new ThreadLocal<Object>();

	@Pointcut("@annotation(com.so_mini.tcc.annotation.Tcc)")
    public void tccAspect() {
        System.out.println("Tcc");
    }
    
    @Pointcut("@annotation(com.so_mini.tcc.annotation.TccEach)")
    public void tccEachAspect() {
        System.out.println("TccEach");
    }

    //环绕主方法
    @Around("tccAspect()")
    public Object aroundMethod(ProceedingJoinPoint pjd) {
        Object result = null;
        String methodName = pjd.getSignature().getName();
        Map<String,Object> tccInfo=new HashMap<String,Object>();
        tccInfo.put("list", new ArrayList<ProceedingJoinPoint>());
        threadLocal.set(tccInfo);
        //执行目标方法
        try {
            //前置通知
            result = pjd.proceed();
            //返回通知
            Map<String,Object> tccInfoRe=(Map<String, Object>) threadLocal.get();
            List<ProceedingJoinPoint> participant=(List<ProceedingJoinPoint>)(tccInfoRe.get("list"));
            System.out.println("【正常,需确认数】"+participant.size());
            for(ProceedingJoinPoint pj:participant){
				toConfirm(pj, _CCFailListenerList);
            }
//            participant.forEach(joinPoint-->(org.aspectj.lang.JoinPoint)joinPoint);
            return result;
        } catch (Throwable e) {
            //异常通知
            Map<String,Object> tccInfoRe=(Map<String, Object>) threadLocal.get();
            List<ProceedingJoinPoint> participant=(List<ProceedingJoinPoint>)(tccInfoRe.get("list"));
            System.out.println("【有问题，取消已完成数】"+participant.size());
            for(ProceedingJoinPoint pj:participant){
				toCancle(pj,_CCFailListenerList);
            }
//            throw new RuntimeException(e);
            return null;
        }
        finally{
        //后置通知
        	threadLocal.remove();
			System.out.println("【threadLocal removed!】");//The method " + methodName + " ends and ");
        }
        
    }
    
    //环绕每个tcc的try方法
    @Around("tccEachAspect()")
    public Object aroundEachMethod(ProceedingJoinPoint pjd) {
        Object result = null;
        String methodName = pjd.getSignature().getName();
        Map tccInfo=(Map) threadLocal.get();
        List participant=(List)(tccInfo.get("list"));
        try {
            //先存起来
        	participant.add(pjd);
            result = pjd.proceed();
            //返回通知
            System.out.println("["+methodName+"] return value:"+result);
            setTryResult(pjd,result);
            return result;
        } catch (Throwable e) {
            //异常后再取消，抛异常
        	participant.remove(pjd);
            throw new RuntimeException(e);
        }
        finally{
        //后置通知
//        	System.out.println("The method " + methodName + " ends");
        }
        
    }
    
    //正常try，第二个参数记录结果
    public static void setTryResult(ProceedingJoinPoint point,Object result) {
		int i=0;
		Object[] args = point.getArgs();
		args[1]=result;
	}
    
    //确认时，第一个参数修改
    public static void toConfirm(ProceedingJoinPoint point,List<CCFailListener> cCFailListenerList)  {
		Object[] args = point.getArgs();
		args[0]=(Object)(TccPhaseEnum.Confirm);
		try {
			point.proceed(args);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
			cCFailListenerList.forEach(listener->listener.notify(point, TccPhaseEnum.Confirm));
		}
	}
    //回滚时，第一个参数修改
    public static void toCancle(ProceedingJoinPoint point,List<CCFailListener> cCFailListenerList){
		Object[] args = point.getArgs();
		args[0]=(Object)(TccPhaseEnum.Cancel);
		try {
			point.proceed(args);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			cCFailListenerList.forEach(listener->listener.notify(point, TccPhaseEnum.Cancel));
//			e.printStackTrace();
		}
	}
	
}
