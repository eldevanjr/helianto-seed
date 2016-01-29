/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.helianto.security.controller;


import javax.inject.Inject;

import org.helianto.core.domain.Identity;
import org.helianto.core.domain.Signup;
import org.helianto.core.repository.IdentityRepository;
import org.helianto.security.domain.IdentitySecret;
import org.helianto.security.internal.UserAuthentication;
import org.helianto.security.repository.IdentitySecretRepository;
import org.helianto.security.service.SignupService;
import org.helianto.sender.service.PasswordRecoverySender;
import org.helianto.user.domain.User;
import org.helianto.user.repository.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;


/**
 * Spring MVC password recovery controller.
 * 
 * @author mauriciofernandesdecastro
 */
@Controller
@RequestMapping(value="/recovery")
public class PasswordRecoveryController extends AbstractCryptoController{
	
	public static final String FRAME_SECURITY = "frame-security";
	
	public static final String REDIRECT_LOGIN =  "redirect:/login/";
	@Inject 
	private IdentityRepository identityRepository;
	
	@Inject
	private Environment env;
	
	@Inject 
	private UserRepository userRepository;
	
	@Inject
	private IdentitySecretRepository identitySecretRepository;
	
	@Inject
	private SignupService signupService;
	
	@Inject
	private PasswordRecoverySender passwordRecoverySender;
	
	
	/**
	 * Password recovery e-mail.
	 */
	@RequestMapping(value={"/recovery", ""}, method={ RequestMethod.GET })
	public String recovery(String error, Model model) {
		model.addAttribute("titlePage", "Password recovery");
		model.addAttribute("baseName", "security");
		model.addAttribute("main", "security/passwordRecover");
		model.addAttribute("copyright", env.getProperty("helianto.copyright", ""));
		if (error!=null && error.equals("1")) {
			model.addAttribute("error", "1");
		}
		return FRAME_SECURITY;
	}
		
	/**
	 * Password change form (when user is already registered).
	 * 
	 * @param userAuthentication
	 * @param model
	 */
	@PreAuthorize("isAuthenticated()")
	@RequestMapping(value={"/self", "self"}, method= {RequestMethod.POST, RequestMethod.GET })
	public String change(UserAuthentication userAuthentication, Model model) {
		try {
			User user = userRepository.findOne(userAuthentication.getUserId());
			Identity  identity = user.getIdentity();
			if (identity!=null) {
				model.addAttribute("titlePage", "Password change");
				model.addAttribute("baseName", "security");
				model.addAttribute("main", "security/passwordChange");
				model.addAttribute("copyright", env.getProperty("helianto.copyright", ""));
				model.addAttribute("email", identity.getPrincipal());
				return FRAME_SECURITY;
				
			}
			model.addAttribute("recoverFailMsg", "label.user.password.recover.fail.message.1");
			model.addAttribute("recoverFail", "true");
		} catch (Exception e) {
			model.addAttribute("recoverFailMsg", e.getMessage());
			model.addAttribute("recoverFail", "true");
		}
		
		return REDIRECT_LOGIN;
		
	}
	
	/**
	 * Password change submission.
	 * 
	 * @param model
	 * @param email
	 * @param password
	 */
	@RequestMapping(value="/submit", method= RequestMethod.POST)
	public String recover(Model model, @RequestParam String email, @RequestParam String password) {
		model.addAttribute("titlePage", "Password change");
		model.addAttribute("baseName", "security");
		model.addAttribute("main", "security/passwordChange");
		model.addAttribute("copyright", env.getProperty("helianto.copyright", ""));
		
		Identity identity = identityRepository.findByPrincipal(email);
		if (identity!=null) {
			IdentitySecret identitySecret = identitySecretRepository.findByIdentityKey(identity.getPrincipal());
			model.addAttribute("email", identity.getPrincipal());
			
			//check for the same password
			if(BCrypt.checkpw(password, identitySecret.getIdentitySecret())){
				model.addAttribute("titlePage", "Mudança de Senha");
				model.addAttribute("baseName", "security");
				model.addAttribute("main", "security/passwordChange");
				model.addAttribute("copyright", env.getProperty("helianto.copyright", ""));
				model.addAttribute("recoverFail", "true");
				model.addAttribute("recoverFailMsg", "label.user.password.recover.fail.message.0");
				return FRAME_SECURITY;
			}
			changeIdentitySecret(identity.getPrincipal(), password);
			model.addAttribute("recoverFail", "false");
			return "redirect:/home/";
		}
		model.addAttribute("recoverFailMsg", "label.user.password.recover.fail.message.1");
		model.addAttribute("recoverFail", "true");
		
		return FRAME_SECURITY;
	}

	/**
	 * Send password retrieval e-mail.
	 * 
	 * @param model
	 * @param principal
	 */
	@RequestMapping(value="/send", method= {RequestMethod.POST, RequestMethod.GET })
	public String send(Model model, @RequestParam(required=false) String principal) {
		model.addAttribute("titlePage", "Password recovery");
		model.addAttribute("baseName", "security");
		model.addAttribute("main", "security/passwordRecover");
		model.addAttribute("copyright", env.getProperty("helianto.copyright", ""));
		
		if (principal==null) {
			model.addAttribute("recoverFailMsg", "label.user.password.recover.fail.message.1");
			model.addAttribute("recoverFail", "true");
			return FRAME_SECURITY;
		}
		
		try {
			Identity recipient = identityRepository.findByPrincipal(principal);
			
			if (recipient!=null) {
				Signup signup = new Signup(recipient);
				signup.setToken(signupService.createToken());
				signup = signupService.saveSignup(signup, null);
				
				if(passwordRecoverySender.send(recipient.getPrincipal(), recipient.getIdentityFirstName(), 
						recipient.getIdentityLastName(), "Password recovery e-mail", "confirmationToken", 
						signup.getToken(),"recipientEmail",recipient.getPrincipal(), 
						"recipientFirstName",recipient.getIdentityFirstName())){	
					model.addAttribute("emailRecoverySent", true);
				}
				else {
					// Caso falhe o envio, retorna ao formulário de e-mail
					model.addAttribute("emailRecoveryFailed", true);
					return FRAME_SECURITY;
				}	
				return REDIRECT_LOGIN;
			}
			
			model.addAttribute("recoverFailMsg", "label.user.password.recover.fail.message.1");
			model.addAttribute("recoverFail", "true");
			
		} catch (Exception e) {
			model.addAttribute("recoverFailMsg", e.getMessage());
			model.addAttribute("recoverFail", "true");
			
		}
		
		return REDIRECT_LOGIN;
		
	}

	/**
	 * Receive e-mail confirmation and respond with form (unauthenticated users).
	 * 
	 * @param model
	 * @param email
	 * @param identityId
	 */
	@RequestMapping(value="/return/{token}", method=RequestMethod.GET)
	public String mail(Model model, @PathVariable String token) {
		model.addAttribute("titlePage", "Password change");
		model.addAttribute("baseName", "security");
		model.addAttribute("main", "security/passwordChange");
		model.addAttribute("copyright", env.getProperty("helianto.copyright", ""));
		
		int identityId = decriptAndValidateToken(token);
		Identity identity = identityRepository.findOne(identityId);
		if (identity!=null) {
			model.addAttribute("email", identity.getPrincipal());
			return FRAME_SECURITY;
			
		}
		model.addAttribute("recoverFailMsg", "label.user.password.recover.fail.message.1");
		model.addAttribute("recoverFail", "true");
		
		return FRAME_SECURITY;
	}
	
}
