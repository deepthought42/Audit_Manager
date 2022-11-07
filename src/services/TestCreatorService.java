package com.looksee.services;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

import org.openqa.grid.common.exception.GridException;
import org.openqa.selenium.WebDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.looksee.models.Domain;
import com.looksee.models.Element;
import com.looksee.models.Group;
import com.looksee.models.LookseeObject;
import com.looksee.models.PageState;
import com.looksee.models.Test;
import com.looksee.models.TestRecord;
import com.looksee.models.enums.TestStatus;
import com.looksee.utils.BrowserUtils;
import com.looksee.utils.PathUtils;

/**
 * 
 * 
 */
@Component
public class TestCreatorService {
	private static Logger log = LoggerFactory.getLogger(TestCreatorService.class.getName());
	
	@Autowired
	private TestService test_service;

	@Autowired
	private GroupService group_service;

	/**
	 * Generates a landing page test based on a given URL
	 *
	 * @param browser
	 * @param msg
	 *
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws WebDriverException
	 * @throws GridException
	 *
	 * @pre browser != null
	 * @pre msg != null
	 */
	public Test createLandingPageTest(List<String> path_keys, 
									  List<LookseeObject> path_objects, 
									  PageState page_state, 
									  String browser_name, 
									  Domain domain, 
									  long account_id
	 ) throws MalformedURLException, IOException, NullPointerException, GridException, WebDriverException, NoSuchAlgorithmException{
	  	
	  	log.warn("domain url :: "+domain.getUrl());
	  	URL domain_url = new URL(domain.getUrl());
	  	log.warn("total path object added to test :: "+path_objects.size());
	  	Test test = createTest(path_keys, 
	  						   path_objects, 
	  						   page_state, 
	  						   1L, 
	  						   browser_name, 
	  						   domain_url.getHost(), 
	  						   account_id);

	  	String url = BrowserUtils.sanitizeUrl(page_state.getUrl(), false);
		
		String url_path = new URL(url).getPath();
		url_path = url_path.replace("/", " ").trim();
		if(url_path.isEmpty()){
			url_path = "home";
		}
		test.setName(url_path + " page loaded");

		//add group "smoke" to test
		Group group = new Group("smoke");
		group = group_service.save(group);
		test.addGroup(group);

		return test;
	}	

	/**
	 * Generates {@link Test Tests} for test
	 * @param test
	 * @param result_page
	 * @throws JsonProcessingException
	 * @throws MalformedURLException
	 */
	public Test createTest(
			List<String> path_keys, 
			List<LookseeObject> path_objects, 
			PageState result_page, 
			long crawl_time, 
			String browser_name, 
			String domain_host,
			long account_id
	) throws JsonProcessingException, MalformedURLException {
		assert path_keys != null;
		assert path_objects != null;
		assert result_page != null;
		
		String result_url = result_page.getUrl();
		log.warn("Creating test........");
		
		log.warn("path objects ::  "+path_objects.size());
		String last_page_state_url = PathUtils.getLastPageStateOLD(path_objects).getUrl();
		boolean leaves_domain = !(domain_host.trim().equals(new URL(result_url).getHost()) || result_url.contains(new URL(last_page_state_url).getHost()));
		Test test = new Test(path_keys, path_objects, result_page, leaves_domain);

		Test test_db = test_service.findByKey(test.getKey(), domain_host, account_id);
		if(test_db == null){
			test.setRunTime(crawl_time);
			test.setLastRunTimestamp(new Date());
			addFormGroupsToPath(test);

			TestRecord test_record = new TestRecord(test.getLastRunTimestamp(), TestStatus.UNVERIFIED, browser_name, result_page, crawl_time, test.getPathKeys());
			test.addRecord(test_record);
			
			return test;
		}

		return test;
	}

	/**
	 * Adds Group labeled "form" to test if the test has any elements in it that have form in the xpath
	 *
	 * @param test {@linkplain Test} that you want to label
	 * @throws MalformedURLException 
	 */
	private void addFormGroupsToPath(Test test) throws MalformedURLException {
		//check if test has any form elements
		for(LookseeObject path_obj: test.getPathObjects()){
			if(path_obj.getClass().equals(Element.class)){
				Element elem = (Element)path_obj;
				if(elem.getXpath().contains("form")){
					test.addGroup(new Group("form"));
					//test_service.save(test);
					break;
				}
			}
		}
	}
}
