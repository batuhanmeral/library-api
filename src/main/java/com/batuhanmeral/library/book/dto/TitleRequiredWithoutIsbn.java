package com.batuhanmeral.library.book.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** İki alanlı kural: başlık sadece ISBN varken boş olabilir. Hata "title" alanına yazılır. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TitleRequiredWithoutIsbn.Validator.class)
public @interface TitleRequiredWithoutIsbn {

    String message() default "must not be blank when no isbn is given";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TitleRequiredWithoutIsbn, NewBookRequest> {

        @Override
        public boolean isValid(NewBookRequest request, ConstraintValidatorContext context) {
            if (request == null || request.hasTitle() || request.isbn() != null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("title")
                    .addConstraintViolation();
            return false;
        }
    }
}
