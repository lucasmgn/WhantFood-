package com.wantfood.aplication.api.exceptionhandler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import com.wantfood.aplication.core.validation.ValidacaoException;
import com.wantfood.aplication.domain.exception.EntidadeEmUsoException;
import com.wantfood.aplication.domain.exception.EntidadeNaoEncontradaException;
import com.wantfood.aplication.domain.exception.NegocioException;
 
/*
 * Classe global, para todos os controladores
 * extends ResponseEntityExceptionHandler classe que indentifica erros internos
 * */
@ControllerAdvice 
public class ApiExceptionHandler extends ResponseEntityExceptionHandler{
	
	private static final String USER_MESSAGE = "Ocorreu um erro interno inesperado no sistema. "
	        + "Tente novamente e se o problema persistir, entre em contato "
	        + "com o administrador do sistema.";
	
	//Criado para acessar a getMessage colocando o fieldError e o locale
	@Autowired
	private MessageSource messageSource;
	
	@ExceptionHandler({ ValidacaoException.class })
	public ResponseEntity<Object> handleValidacaoException(ValidacaoException ex, WebRequest request) {
	    return handleValidationInternal(ex, ex.getBindingResult(), new HttpHeaders(), 
	            HttpStatus.BAD_REQUEST, request);
	}
	
	 //BindingResult bindingResult Instancia que armazena as constraints de viola????es, tem acesso em quais fildes foram violadas
	private ResponseEntity<Object> handleValidationInternal(Exception e, BindingResult bindingResult,
			HttpHeaders headers, HttpStatus status, WebRequest request){
			
		ProblemType problemType = ProblemType.DADOS_INVALIDOS;
		String detail = "Um ou mais campos inv??lidos. Fa??a o preenchimento correto e tente novamente.";
		
	    /*
	     * Transformando o bindingResult em uma lista de problemFields,
	     * mudando de getFieldErrors para AllErrors, com o bjetivo de pegar n??o apenas
	     *  os erros dos atributos, mas tabm??m os erros da classes 
	     * */
		 List<Problem.Object> problemObjects = bindingResult.getAllErrors().stream()
		    		.map(objectError -> {

		    			/*
		    			 * recebe o valor para passar na .userMessage,
		    			 *  objetivo ?? ler o arquivo message.properties, o parametro mudou de fieldError para
		    			 *  objectError pq agora estou tratando de erros do objeto tamb??m
		    			 * */
		    			String message = messageSource.getMessage(objectError, LocaleContextHolder.getLocale());
		    			
		    			String name = objectError.getObjectName().toUpperCase();
		    			
		    			//Verificando se o objectError ?? um fieldError
		    			if(objectError instanceof FieldError) {
		    				name = ((FieldError) objectError).getField();
		    			}
		    			
		    			return Problem.Object.builder()
		    				.name(name)//Pegando o nome da prorpiedade que foi violada
		    				.userMessage(message)// .getDefaultMessage(), pegando a mensagem padr??o
		    				.build();
		    			})
		    		.collect(Collectors.toList());
		 
		 Problem problem = createProblemBuilder(status, problemType, detail)
				 .userMessage(detail)
				 .objects(problemObjects)
				 .build();
		    
				return handleExceptionInternal(e, problem, headers, status, request);
	}
	
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
			HttpHeaders headers, HttpStatus status, WebRequest request) {

	    return handleValidationInternal(e, e.getBindingResult(), headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> handleUncaught(Exception e, WebRequest request){
		
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		ProblemType problemType = ProblemType.ERRO_DE_SISTEMA;
		
		String detail = USER_MESSAGE;
		
		 /*
		  * Importante colocar o printStackTrace (pelo menos por enquanto, que n??o tem
		  *  o logging) para mostrar a stacktrace no console
		  *  Se n??o fizer isso, voc?? n??o vai ver a stacktrace de exceptions que seriam importantes
		  *  para voc?? durante, especialmente na fase de desenvolvimento     	
		  * */ 
		
		e.printStackTrace();
		
		Problem problem = createProblemBuilder(status, problemType, detail).build();

		return handleExceptionInternal(e, problem, new HttpHeaders(), status, request);
	}
	
	@Override
	protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException e,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
			
		ProblemType problemType = ProblemType.RECURSO_NAO_ENCONTRADO;	


	    String detail = String.format("O recurso %s, que voc?? tentou acessar, ?? inexistente.", 
	            e.getRequestURL());

	    Problem problem = createProblemBuilder(status, problemType, detail).build();

	    return handleExceptionInternal(e, problem, headers, status, request);
	}
	
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers,
	        HttpStatus status, WebRequest request){
		if(e instanceof MethodArgumentTypeMismatchException) {
			return handleMethodArgumentTypeMismatch(
	                (MethodArgumentTypeMismatchException) e, headers, status, request);
		}
		
		return super.handleTypeMismatch(e, headers, status, request);
	}
	
	
	private ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		
		ProblemType problemType = ProblemType.PARAMETRO_INVALIDO;

	    String detail = String.format("O par??metro de URL '%s' recebeu o valor '%s', "
	            + "que ?? de um tipo inv??lido. Corrija e informe um valor compat??vel com o tipo %s.",
	            e.getName(), e.getValue(), e.getRequiredType().getSimpleName());

	    Problem problem = createProblemBuilder(status, problemType, detail).build();

	    return handleExceptionInternal(e, problem, headers, status, request);
	}
	
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
			HttpHeaders headers, HttpStatus status, WebRequest request) {	
		/*
		 * Pegando a raiz do problema para ser passado como o erro pro usu??rio,
		 * afim de especificar melhor a mensagem de erro 
		 * Pegando a causa raiz
		 * */
		Throwable rootCause = ExceptionUtils.getRootCause(e);
		
		/*
		 *Fazendo a verifica????o, se a causa raiz(rootCause) for uma InvalidFormatException ent??o
		 * chamar?? o metodo que ir?? tratar de forma especifica
		 * */
		if(rootCause instanceof InvalidFormatException) {
			return handleInvalidFormat((InvalidFormatException) rootCause, headers, status, request);
			
		}else if(rootCause instanceof PropertyBindingException) {
			return handlePropertyBinding((PropertyBindingException) rootCause, headers, status, request);
		}
		
		ProblemType problemType = ProblemType.ERRO_NA_MENSAGEM;
		String detail = "O corpo est?? requisi????o est?? inv??lido. Possivel erro na sintaxe.";
		
		Problem problem = createProblemBuilder(status, problemType, detail).build();
		
		return handleExceptionInternal(e, problem, headers, status, request);
	}
	
	/*
	 * M??todo a ser chamado se a causa raiz for um IgnoredPropertyException ou UnrecongnizePropertyException
	 * */
	private ResponseEntity<Object> handlePropertyBinding(
			PropertyBindingException e,HttpHeaders headers, HttpStatus status, WebRequest request) {
		/*
		 * IgnoredPropertyException e UnrecongnizePropertyException que extends PropertyBindingException
		 * Criando vari??vel que ir?? pegar o Path da exception
		 * e ir?? formatar o nome dela, delimitando por um '.'
		 * */
		String path = joinPath(e.getPath());
		
		ProblemType problemType = ProblemType.ERRO_NA_MENSAGEM;
		String detail = String.format("A propriedade '%s' n??o existe. "
				+ "Corrija ou remova essa propriedade e tente novamente.", path);
		
		Problem problem = createProblemBuilder(status, problemType, detail)
				.userMessage(USER_MESSAGE)
				.build();
		
		return handleExceptionInternal(e, problem, headers, status, request);
	}

	/*
	 * M??todo a ser chamado se a causa raiz for um InvalidFormatException
	 * */
	private ResponseEntity<Object> handleInvalidFormat(InvalidFormatException e,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		
		/*
		 * InvalidFormatException
		 * Criando vari??vel que ir?? pegar o Path da exception
		 * e ir?? formatar o nome dela, delimitando por um '.'
		 * */
		 String path = joinPath(e.getPath());
		
		ProblemType problemType = ProblemType.ERRO_NA_MENSAGEM;
		String detail = String.format("A propriedade '%s' recebeu o valor '%s',"
				+ " que ?? de um tipo inv??lido. Corrija e informe um valor compat??vel com o tipo %s.",
				path, e.getValue(), e.getTargetType().getSimpleName());
		
		Problem problem = createProblemBuilder(status, problemType, detail)
				.userMessage(USER_MESSAGE)
				.build();
		
		return handleExceptionInternal(e, problem, headers, status, request);
	}

	/*
	 * Criando metodo para controlar os consoles de erro
	 * @ExceptionHandler Aceita mais de um argumento, pode ser usado {EstadoNaoEncontradoException.class},
	 * {EstadoeXEMPLOException.class}
	 * */
	@ExceptionHandler(EntidadeNaoEncontradaException.class)
	public ResponseEntity<?> handleEntidadeNaoEncontrada(EntidadeNaoEncontradaException e,
			WebRequest request){
		
		HttpStatus status = HttpStatus.NOT_FOUND;
		ProblemType problemType = ProblemType.RECURSO_NAO_ENCONTRADO;
		String detail = e.getMessage();
		
		Problem problem = createProblemBuilder(status, problemType, detail).build();

		return handleExceptionInternal(e, problem, new HttpHeaders(), status, request);
	}
	
	@ExceptionHandler(NegocioException.class)
	public ResponseEntity<?> handleNegocio(NegocioException e, WebRequest request){
		
		HttpStatus status = HttpStatus.BAD_REQUEST;
		ProblemType problemType = ProblemType.ERRO_NEGOCIO;
		String detail = e.getMessage();
		
		Problem problem = createProblemBuilder(status, problemType, detail).build();
		
		return handleExceptionInternal(e, problem, new HttpHeaders(), status, request);
	}
	
	@ExceptionHandler(EntidadeEmUsoException.class)
	public ResponseEntity<?> handleEntidadeEmUso(EntidadeEmUsoException e, WebRequest request){

		HttpStatus status = HttpStatus.CONFLICT;
		ProblemType problemType = ProblemType.ENTIDADE_EM_USO;
		String detail = e.getMessage();
		
		Problem problem = createProblemBuilder(status, problemType, detail)
				.userMessage(detail)
				.build();
		
		return handleExceptionInternal(e, problem, new HttpHeaders(),status, request);
	}  
	
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body,
			HttpHeaders headers, HttpStatus status, WebRequest request) {	
		/*
		 * Se o corpo for nulo ser?? aplicado as mensagens padr??o do spring,
		 * caso n??o seja a mensagem criada de exception ser?? apresentada
		 */ 
		if(body == null) {
			body = Problem.builder()
					.timestamp(OffsetDateTime.now())
					.title(status.getReasonPhrase()) //descreve o status que retorna na repsosta
					.status(status.value())
					.userMessage(USER_MESSAGE) //Mensagem para o usu??rio
					.build();
		}else if(body instanceof String) {
			body = Problem.builder()
					.timestamp(OffsetDateTime.now())
					.title((String) body) //descreve o status que retorna na repsosta
					.status(status.value())
					.userMessage(USER_MESSAGE)
					.build();
		}
		
		return super.handleExceptionInternal(e, body, headers, status, request);
	}
	
	//Metodo para ser utilizado para criar um build de problem, facilitando a manuten????o
	private Problem.ProblemBuilder createProblemBuilder(HttpStatus status,
			ProblemType problemType, String detail){
		
		return Problem.builder()
				.timestamp(OffsetDateTime.now())
				.status(status.value())
				.type(problemType.getUri())
				.title(problemType.getTitle())
				.detail(detail);
	}

	
	//M??todo joinPath ir?? concatenar os nomes das propriedades, separando-as por "." 
	private String joinPath(List<Reference> references) {
	    return references.stream()
	        .map(ref -> ref.getFieldName())
	        .collect(Collectors.joining("."));
	} 
}
