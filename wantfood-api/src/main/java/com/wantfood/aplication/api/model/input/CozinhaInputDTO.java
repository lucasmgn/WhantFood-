package com.wantfood.aplication.api.model.input;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CozinhaInputDTO {
	
	@NotBlank
	private String nome;
}
