# tcc-mini 简介

看到一些先进复杂的TCC框架，可多数情况使用TCC是很简单的，于是想通过AOP中将参与者的方法调用信息保存在ThreadLocal中，进行TCC控制，同时还可以回滚本地DB事务。于是花了一下午时间练练手。

主要特点：

- 非常简单的框架，但有结构约定，**以一种投机取巧的方式处理TCC调用的切换与TRY返回值的使用
- 可以实现本地与所有TCC参与者TRY都成功了进行CONFIRM，任何失败都将CANCEL，本地事务回滚
- 对于CONFIRM/CANCEL的失败，由使用者开发实现CCFailListener的接口来处理



# tcc-mini

tcc-mini** is an very simple implementation of Distributed Transaction Manager, based on Try-Confirm-Cancel (TCC) mechanism.

**tcc-mini**  integrated with Spring and use Aop and Threadlocal to record the participants and do confirm them when all try methods succeed，do cancel when any one failed in try method.

## 1. Quick Start

#### 1.1 Add maven depenency

```
		<dependency>
			<groupId>com.so-mini</groupId>
			<artifactId>tcc-mini</artifactId>
			<version>0.0.2-SNAPSHOT</version>
		</dependency>
```

#### 1.2 Add participant services

##### **1st service**

```java
@Service
public class EatBook {
    //1.try comfirm cancel in the same method
    //2.first para is TCC phase, second is TRY result maybe used by confirm or cancel
    //3.the other para is from the 3rd position
	@TccEach
	public void bookEat(TccPhaseEnum type, Object tryResult, String orderId) {
		switch (type) {
		case Try:
			System.out.println("Eat book...!");
			if ("111".equals(orderId))
				throw new RuntimeException("eat failure");
			break;
		case Confirm:
			System.out.println("Eat confirm...!");
			break;
		case Cancel:
			System.out.println("Eat cnacel...!");
			break;
		}
	}
}
```

##### **2rd service**

```JAVA
@Service
public class HouseBook {
	@TccEach
	public String bookHouse(TccPhaseEnum type,Object tryResult,String orderId){
		switch (type) {
		case Try:
			System.out.println("house book...!");
			if("222".equals(orderId))
				throw new RuntimeException("house failure");
			return "HS100083";//+new Random().nextInt()
		case Confirm:
			confirmHouse(tryResult);
			return null;
		case Cancel:
			System.out.println("house cancel...!"+tryResult);
			return null;
		}
		return null;
	}
    private void confirmHouse(Object houseId){
        if("HS100083".equals(houseId)) 
            System.out.println("house confirm...!"+houseId);
        else{
            throw new RuntimeException("houseId missing and confirme failure");
        }
    }
}
```

##### **3rd service**

```java
@Service
public class PlaneBook {
	@TccEach
	public void bookPlane(TccPhaseEnum type,Object tryResult,String orderId){
		switch (type) {
		case Try:
			if("333".equals(orderId))
				throw new RuntimeException("plane failure");
			System.out.println("plane booked!");
			break;
		case Confirm:
			System.out.println("plane confirmed!");
			if("333333".equals(orderId)) throw new RuntimeException("plane Confirm failure");
			break;
		case Cancel:
			System.out.println("plane cnaceled!");
			break;
		}
	}
}
```

##### **aggregation service**(聚合服务)

```java
@Service
public class TripService {
	@Autowired
	private EatBook eatBook;
	@Autowired
	private HouseBook houseBook;
	@Autowired
	private PlaneBook planeBook;
    
    	@Transactional
	@Tcc
	public void createTrip(String orderId){
        	System.out.println("BEGIN db-trans...");
		staffService.addStaff(orderId);//简单的本地数据库操作
		System.out.println("BEGIN remote...");
		eatBook.bookEat(TccPhaseEnum.Try,null,orderId);
		houseBook.bookHouse(TccPhaseEnum.Try,null,orderId);
		planeBook.bookPlane(TccPhaseEnum.Try,null,orderId);
	}
}
```

#### **1.3 listen the confirm/cancel failure**

```java
@Component
public class FailCCDispatcherListener implements CCFailListener{
	@Override
	public void notify(ProceedingJoinPoint pjp, TccPhaseEnum phase) {
		//you can record/retry or dispatch to diffierent handler to do deal the failed CONFIRM/CANCEL here
		System.out.println("[loger:]"+phase.name()+" method:"+pjp.getSignature().getName()+" is failure");
	}
}
```

#### **1.4 result sample **

```java
//第三个TCC出错，前两个执行CANCEL，本地DB事务回滚
BEGIN db-trans...
Hibernate: select staffbo0_.id .....
BEGIN remote...
Eat book...!//第一个参与者 try
[bookEat] return value:null//try 的 返回值
house book...!//第二个参与者 try
[bookHouse] return value:HS100083//try 的 返回值
【有问题，取消已完成数】2//第三个失败了，取消前两个成功的try
Eat cnacel...!//第一个参与者
house cancel...!HS100083//第二个参与者 cancel使用了try的结果
【threadLocal removed!】//all finished
java.lang.RuntimeException: TCC异常
```

```java
//正常情况
BEGIN db-trans...
Hibernate: select staffbo0_.id ...
BEGIN remote...
Eat book...!
[bookEat] return value:null
house book...!
[bookHouse] return value:HS100083
plane booked!
[bookPlane] return value:null
【正常,需确认数】3
Eat confirm...!
house confirm...!HS100083
plane confirmed!
【threadLocal removed!】
Hibernate: insert into staff (age, name, id) values (?, ?, ?)
```



## 2. Features

- very very simple Dsolution, just record the participant in Threadlocal and invoke three kind of method. if try method fail , just throw exception. all the successes Try s will Canceled.
- must write three invoke in one method with @TccEach and the first para is TCC type, the second is the result of try method which may used in Confirm/Cancel.
- in @Around of Aspectj , the 1st/2rd para of ProceedingJoinPoint will Changed to realized the function.
- if Confirm/Cancel failure, the implements of the interface CCFailListener will deal it, you can get all info from the ProceedingJoinPoint.
