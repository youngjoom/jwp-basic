package core.di.factory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import core.annotation.Controller;
import core.di.factory.config.BeanDefinition;
import core.di.factory.config.InjectType;
import core.di.factory.support.BeanDefinitionRegistry;

public class BeanFactory implements BeanDefinitionRegistry {
	private static final Logger log = LoggerFactory.getLogger(BeanFactory.class);
	
	private Map<Class<?>, Object> beans = Maps.newHashMap();
	
	private Map<Class<?>, BeanDefinition> beanDefinitions = Maps.newHashMap();
	
    public void initialize() {
    	for (Class<?> clazz : beanDefinitions.keySet()) {
			getBean(clazz);
		}
    }
	
	@SuppressWarnings("unchecked")
	public <T> T getBean(Class<T> clazz) {
		Object bean = beans.get(clazz);
		if (bean != null) {
			return (T)bean;
		}
		
		Class<?> concreteClass = findBeanClass(clazz);
		BeanDefinition beanDefinition = beanDefinitions.get(concreteClass);
		bean = inject(beanDefinition);
		registerBean(concreteClass, bean);
		return (T)bean;
	}
	
	private Class<?> findBeanClass(Class<?> clazz) {
		Set<Class<?>> beanClasses = beanDefinitions.keySet();
    	Class<?> concreteClazz = BeanFactoryUtils.findConcreteClass(clazz, beanClasses);
        if (!beanClasses.contains(concreteClazz)) {
            throw new IllegalStateException(clazz + "는 Bean이 아니다.");
        }
        return concreteClazz;
    }

	private Object inject(BeanDefinition beanDefinition) {
		if (beanDefinition.getResolvedInjectMode() == InjectType.INJECT_NO) {
			return BeanUtils.instantiate(beanDefinition.getBeanClass());
		} else if (beanDefinition.getResolvedInjectMode() == InjectType.INJECT_TYPE){
			return injectFields(beanDefinition);
		} else {
			return injectConstructor(beanDefinition);
		}
	}

	private Object injectConstructor(BeanDefinition beanDefinition) {
		Constructor<?> constructor = beanDefinition.getInjectConstructor();
		List<Object> args = Lists.newArrayList();
		for (Class<?> clazz : constructor.getParameterTypes()) {
		    args.add(getBean(clazz));
		}
		return BeanUtils.instantiateClass(constructor, args.toArray());
	}

	private Object injectFields(BeanDefinition beanDefinition) {
		Object bean = BeanUtils.instantiate(beanDefinition.getBeanClass());
		Set<Field> injectFields = beanDefinition.getInjectFields();
		for (Field field : injectFields) {
			injectField(bean, field);
		}
		return bean;
	}

	private void injectField(Object bean, Field field) {
		log.debug("Inject Bean : {}, Field : {}", bean, field);
		try {
			field.setAccessible(true);
			field.set(bean, getBean(field.getType()));
		} catch (IllegalAccessException | IllegalArgumentException e) {
			log.error(e.getMessage());
		}
	}

    public void registerBean(Class<?> clazz, Object bean) {
    	beans.put(clazz, bean);
    }

	public Map<Class<?>, Object> getControllers() {
		Map<Class<?>, Object> controllers = Maps.newHashMap();
		for (Class<?> clazz : beanDefinitions.keySet()) {
			Annotation annotation = clazz.getAnnotation(Controller.class);
			if (annotation != null) {
				controllers.put(clazz, beans.get(clazz));
			}
		}
		return controllers;
	}
	
	public void clear() {
		beanDefinitions.clear();
		beans.clear();
	}

	@Override
	public void registerBeanDefinition(Class<?> clazz, BeanDefinition beanDefinition) {
		log.debug("register bean : {}", clazz);
		beanDefinitions.put(clazz, beanDefinition);
	}
}
