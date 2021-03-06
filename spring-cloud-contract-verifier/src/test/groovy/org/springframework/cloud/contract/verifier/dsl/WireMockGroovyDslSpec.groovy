/*
 *  Copyright 2013-2016 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.contract.verifier.dsl

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.springframework.cloud.contract.verifier.dsl.wiremock.WireMockStubStrategy
import org.springframework.cloud.contract.verifier.file.ContractMetadata
import org.springframework.cloud.contract.verifier.util.AssertionUtil
import spock.lang.Issue
import spock.lang.Specification

class WireMockGroovyDslSpec extends Specification implements WireMockStubVerifier {

	def 'should convert groovy dsl stub to wireMock stub for the client side'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('GET')
					url $(consumer(~/\/[0-9]{2}/), producer('/12'))
				}
				response {
					status 200
					body(
							id: value(
									consumer('123'),
									producer(regex('[0-9]+'))
							),
							surname: $(
									consumer('Kowalsky'),
									producer(regex('[a-zA-Z]+'))
							),
							name: 'Jan',
							created: $(consumer('2014-02-02 12:23:43'), producer({ currentDate(it) }))
					)
					headers {
						header 'Content-Type': 'text/plain'
					}
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
			AssertionUtil.assertThatJsonsAreEqual('''
			{
			  "request" : {
				"urlPattern" : "/[0-9]{2}",
				"method" : "GET"
			  },
			  "response" : {
				"status" : 200,
				"body" : "{\\"id\\":\\"123\\",\\"surname\\":\\"Kowalsky\\",\\"name\\":\\"Jan\\",\\"created\\":\\"2014-02-02 12:23:43\\"}",
				"headers" : {
				  "Content-Type" : "text/plain"
				}
			  }
			}
			''', wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	@Issue("#79")
	def 'should convert groovy dsl stub to wireMock stub for the client side with a body containing a map'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url '/ingredients'
					headers {
						header 'Content-Type': 'application/vnd.pl.devoxx.aggregatr.v1+json'
					}
				}
				response {
					status 200
					body(
							ingredients: [
									[type: 'MALT', quantity: 100],
									[type: 'WATER', quantity: 200],
									[type: 'HOP', quantity: 300],
									[type: 'YIEST', quantity: 400]
							]
					)
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual('''
{
  "request" : {
	"url" : "/ingredients",
	"method" : "GET",
	"headers" : {
	  "Content-Type" : {
		"equalTo" : "application/vnd.pl.devoxx.aggregatr.v1+json"
	  }
	}
  },
  "response" : {
	"status" : 200,
	"body" : "{\\"ingredients\\":[{\\"type\\":\\"MALT\\",\\"quantity\\":100},{\\"type\\":\\"WATER\\",\\"quantity\\":200},{\\"type\\":\\"HOP\\",\\"quantity\\":300},{\\"type\\":\\"YIEST\\",\\"quantity\\":400}]}"
  }
}
''', wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	@Issue("#86")
	def 'should convert groovy dsl stub with GString and regexp'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('POST')
					url('/ws/payments')
					headers {
						header("Content-Type": 'application/x-www-form-urlencoded')
					}
					body("""paymentType=INCOMING&transferType=BANK&amount=${
						value(consumer(regex('[0-9]{3}\\.[0-9]{2}')), producer(500.00))
					}&bookingDate=${
						value(consumer(regex('[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])')), producer('2015-05-18'))
					}""")
				}
				response {
					status 204
					body(
							paymentId: value(consumer('4'), producer(regex('[1-9][0-9]*'))),
							foundExistingPayment: false
					)
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual('''
{
	"request": {
		"method": "POST",
		"headers": {
			"Content-Type": {
				"equalTo": "application/x-www-form-urlencoded"
			}
		},
		"url": "/ws/payments",
		"bodyPatterns": [
			{
				"matches": "paymentType=INCOMING&transferType=BANK&amount=[0-9]{3}\\\\.[0-9]{2}&bookingDate=[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])"
			}
		]
	},
	"response": {
		"status": 204,
		"body": "{\\"paymentId\\":\\"4\\",\\"foundExistingPayment\\":false}"
	}
}
''', wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	def 'should convert groovy dsl stub with Body as String to wireMock stub for the client side'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('GET')
					url $(consumer(~/\/[0-9]{2}/), producer('/12'))
				}
				response {
					status 200
					body("""\
							{
								"id": "${value(consumer('123'), producer('321'))}",
								"surname": "${value(consumer('Kowalsky'), producer(regex('[a-zA-Z]+')))}",
								"name": "Jan",
								"created" : "${$(consumer('2014-02-02 12:23:43'), producer('2999-09-09 01:23:45'))}"
							}
						"""
					)
					headers {
						header 'Content-Type': 'text/plain'
					}
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
{
  "request" : {
	"urlPattern" : "/[0-9]{2}",
	"method" : "GET"
  },
  "response" : {
	"status" : 200,
	"body" : "{\\"created\\":\\"2014-02-02 12:23:43\\",\\"id\\":\\"123\\",\\"name\\":\\"Jan\\",\\"surname\\":\\"Kowalsky\\"}",
	"headers" : {
	  "Content-Type" : "text/plain"
	}
  }
}
'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	def 'should convert groovy dsl stub with simple Body as String to wireMock stub for the client side'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('GET')
					url $(consumer(regex('/[0-9]{2}')), producer('/12'))
					body """
						{
							"name": "Jan"
						}
						"""
				}
				response {
					status 200
					body("""\
							{
								"name": "Jan"
							}
						"""
					)
					headers {
						header 'Content-Type': 'text/plain'
					}
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual('''
{
  "request" : {
	"urlPattern" : "/[0-9]{2}",
	"method" : "GET",
	"bodyPatterns" : [ {
	  "matchesJsonPath" : "$[?(@.name == 'Jan')]"
	} ]
  },
  "response" : {
	"status" : 200,
	"body" : "{\\"name\\":\\"Jan\\"}",
	"headers" : {
	  "Content-Type" : "text/plain"
	}
  }
}
''', wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	def 'should use equalToJson when body match is defined as map'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('GET')
					url $(consumer(~/\/[0-9]{2}/), producer('/12'))
					body(
							id: value(
									consumer(regex('[0-9]+')),
									producer('123'),
							),
							surname: $(
									consumer(regex('[a-zA-Z]+')),
									producer('Kowalsky'),
							),
							name: 'Jan',
							created: $(consumer('2014-02-02 12:23:43'), producer(execute('currentDate($it)')))
					)
				}
				response {
					status 200
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
{
  "request" : {
	"urlPattern" : "/[0-9]{2}",
	"method" : "GET",
	"bodyPatterns" : [ {
	  "matchesJsonPath" : "$[?(@.created == '2014-02-02 12:23:43')]"
	}, {
	  "matchesJsonPath" : "$[?(@.surname =~ /[a-zA-Z]+/)]"
	}, {
	  "matchesJsonPath" : "$[?(@.name == 'Jan')]"
	}, {
	  "matchesJsonPath" : "$[?(@.id =~ /[0-9]+/)]"
	} ]
  },
  "response" : {
	"status" : 200
  }
}
			'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	def 'should use equalToJson when content type ends with json'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url "/users"
					headers {
						header "Content-Type", "customtype/json"
					}
					body """
							{
								"name": "Jan"
							}
							"""
				}
				response {
					status 200
				}
			}
		when:
			String json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
{
  "request" : {
	"url" : "/users",
	"method" : "GET",
	"bodyPatterns" : [ {
	  "matchesJsonPath" : "$[?(@.name == 'Jan')]"
	} ],
	"headers" : {
	  "Content-Type" : {
		"equalTo" : "customtype/json"
	  }
	}
  },
  "response" : {
	"status" : 200
  }
}
			'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	def 'should use equalToXml when content type ends with xml'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url "/users"
					headers {
						header "Content-Type", "customtype/xml"
					}
					body """<name>${value(consumer('Jozo'), producer('Denis'))}</name><jobId>${
						value(consumer("<test>"), producer('1234567890'))
					}</jobId>"""
				}
				response {
					status 200
				}
			}
		when:
			String json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
				{
					"request": {
						"method": "GET",
						"url": "/users",
						"headers": {
							"Content-Type": {
								"equalTo": "customtype/xml"
							}
						},
						"bodyPatterns": [
							{
								"equalToXml":"<name>Jozo</name><jobId>&lt;test&gt;</jobId>"
							}
						]
					},
					"response": {
						"status": 200
					}
				}
				'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	def 'should use equalToXml when content type is parsable xml'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url "/users"
					body """<user><name>${value(consumer('Jozo'), producer('Denis'))}</name><jobId>${
						value(consumer("<test>"), producer('1234567890'))
					}</jobId></user>"""
				}
				response {
					status 200
				}
			}
		when:
			String json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
					{
						"request": {
							"method": "GET",
							"url": "/users",
							"bodyPatterns": [
								{
									"equalToXml":"<user><name>Jozo</name><jobId>&lt;test&gt;</jobId></user>"
								}
							]
						},
						"response": {
							"status": 200
						}
					}
					'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	def 'should support xml as a response body'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url "/users"
				}
				response {
					status 200
					body """<user><name>${value(consumer('Jozo'), producer('Denis'))}</name><jobId>${
						value(consumer("<test>"), producer('1234567890'))
					}</jobId></user>"""
				}
			}
		when:
			String json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
						{
							"request": {
								"method": "GET",
								"url": "/users"
							},
							"response": {
								"status": 200,
								"body":"<user><name>Jozo</name><jobId>&lt;test&gt;</jobId></user>"
							}
						}
						'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	def 'should use equalToJson'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url "/users"
					body equalToJson('''{"name":"Jan"}''')
				}
				response {
					status 200
				}
			}
		when:
			String json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
				{
					"request": {
						"method": "GET",
						"url": "/users",
						"bodyPatterns": [
							{
								"equalToJson":"{\\"name\\":\\"Jan\\"}"
							}
						]
					},
					"response": {
						"status": 200
					}
				}
				'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	def 'should use equalToXml'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url "/users"
					body equalToXml("""<name>${value(consumer('Jozo'), producer('Denis'))}</name><jobId>${
						value(consumer("<test>"), producer('1234567890'))
					}</jobId>""")
				}
				response {
					status 200
				}
			}
		when:
			String json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
					{
						"request": {
							"method": "GET",
							"url": "/users",
							"bodyPatterns": [
								{
									"equalToXml":"<name>Jozo</name><jobId>&lt;test&gt;</jobId>"
								}
							]
						},
						"response": {
							"status": 200
						}
					}
					'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	def 'should convert groovy dsl stub with regexp Body as String to wireMock stub for the client side'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('GET')
					url $(consumer(regex('/[0-9]{2}')), producer('/12'))
					body """
						{
							"personalId": "${value(consumer(regex('^[0-9]{11}$')), producer('57593728525'))}"
						}
						"""
				}
				response {
					status 200
					body("""\
							{
								"name": "Jan"
							}
					 """
					)
					headers {
						header 'Content-Type': 'text/plain'
					}
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
{
  "request" : {
	"urlPattern" : "/[0-9]{2}",
	"method" : "GET",
	"bodyPatterns" : [ {
	  "matchesJsonPath" : "$[?(@.personalId =~ /^[0-9]{11}$/)]"
	} ]
  },
  "response" : {
	"status" : 200,
	"body" : "{\\"name\\":\\"Jan\\"}",
	"headers" : {
	  "Content-Type" : "text/plain"
	}
  }
}
'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	def 'should convert groovy dsl stub with a regexp and an integer in request body'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'PUT'
					url '/fraudcheck'
					body("""
						{
						"clientPesel":"${value(consumer(regex('[0-9]{10}')), producer('1234567890'))}",
						"loanAmount":123.123
						}
					"""
					)
					headers {
						header('Content-Type', 'application/vnd.fraud.v1+json')
					}

				}
				response {
					status 200
					body(
							fraudCheckStatus: "OK",
							rejectionReason: $(consumer(null), producer(execute('assertThatRejectionReasonIsNull($it)')))
					)
					headers {
						header('Content-Type': 'application/vnd.fraud.v1+json')
					}
				}

			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
{
  "request" : {
	"url" : "/fraudcheck",
	"method" : "PUT",
	"bodyPatterns" : [ {
	  "matchesJsonPath" : "$[?(@.loanAmount == 123.123)]"
	}, {
	  "matchesJsonPath" : "$[?(@.clientPesel =~ /[0-9]{10}/)]"
	} ],
	"headers" : {
	  "Content-Type" : {
		"equalTo" : "application/vnd.fraud.v1+json"
	  }
	}
  },
  "response" : {
	"status" : 200,
	"body" : "{\\"fraudCheckStatus\\":\\"OK\\",\\"rejectionReason\\":null}",
	"headers" : {
	  "Content-Type" : "application/vnd.fraud.v1+json"
	}
  }
}
'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	def "should generate request with urlPath and queryParameters for client side"() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					urlPath($(consumer("users"), producer("items"))) {
						queryParameters {
							parameter 'limit': $(consumer(equalTo("20")), producer("10"))
							parameter 'offset': $(consumer(containing("10")), producer("10"))
							parameter 'filter': "email"
							parameter 'sort': $(consumer(~/^[0-9]{10}$/), producer("1234567890"))
							parameter 'search': $(consumer(notMatching(~/^\/[0-9]{2}$/)), producer("10"))
							parameter 'age': $(consumer(notMatching("^\\w*\$")), producer(10))
							parameter 'name': $(consumer(matching("Denis.*")), producer("Denis"))
							parameter 'credit': absent()
						}
					}
				}
				response {
					status 200
				}
			}
		when:
			def json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
			{
				"request": {
					"method": "GET",
					"urlPath": "users",
					"queryParameters": {
					  "offset": {
						"contains": "10"
					  },
					  "limit": {
						"equalTo": "20"
					  },
					  "filter": {
						"equalTo": "email"
					  },
					  "sort": {
						"matches": "^[0-9]{10}$"
					  },
					  "search": {
						"doesNotMatch": "^/[0-9]{2}$"
					  },
					  "age": {
						"doesNotMatch": "^\\\\w*$"
					  },
					  "name": {
						"matches": "Denis.*"
					  },
					  "credit": {
					  	 "absent": true
					  }
					}
				},
				"response": {
					"status": 200,
				}
			}
			'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	@Issue('#230')
	def "should generate request with urlPathPattern and queryParameters for client side\
			when both contains regular expressions"() {
		given:
		org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
			request {
				method 'GET'
				urlPath($(consumer(regex("/users/[0-9]+")), producer("/users/1"))) {
					queryParameters {
						parameter 'search': $(consumer(notMatching(~/^\/[0-9]{2}$/)), producer("10"))
					}
				}
			}
			response {
				status 200
			}
		}
		when:
		def json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
			{
				"request": {
					"method": "GET",
					"urlPathPattern": "/users/[0-9]+",
					"queryParameters": {
					  "search": {
						"doesNotMatch": "^/[0-9]{2}$"
					  }
					}
				},
				"response": {
					"status": 200,
				}
			}
			'''), json)
		and:
		stubMappingIsValidWireMockStub(json)
	}


	def "should generate request with urlPath for client side"() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					urlPath $(consumer("boxes"), producer("items"))
				}
				response {
					status 200
				}
			}
		when:
			def json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
				{
					"request": {
						"method": "GET",
						"urlPath": "boxes"
					},
					"response": {
						"status": 200,
					}
				}
				'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	def "should generate simple request with urlPath for client side"() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					urlPath "boxes"
				}
				response {
					status 200
				}
			}
		when:
			def json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
				{
					"request": {
						"method": "GET",
						"urlPath": "boxes"
					},
					"response": {
						"status": 200,
					}
				}
				'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	def "should not allow regexp in url for server value"() {
		when:
			org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url(regex(/users\/[0-9]*/)) {
						queryParameters {
							parameter 'age': notMatching("^\\w*\$")
							parameter 'name': matching("Denis.*")
						}
					}
				}
				response {
					status 200
				}
			}
		then:
			def e = thrown(IllegalStateException)
			e.message.contains "Url can't be a pattern for the server side"
	}

	def "should not allow regexp in query parameter for server value"() {
		when:
			org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url("abc") {
						queryParameters {
							parameter 'age': $(consumer(notMatching("^\\w*\$")), producer(regex(".*")))
						}
					}
				}
				response {
					status 200
				}
			}
		then:
			thrown(IllegalStateException)
	}

	def "should not allow query parameter unresolvable for a server value"() {
		when:
			org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					urlPath("users") {
						queryParameters {
							parameter 'age': notMatching("^\\w*\$")
							parameter 'name': matching("Denis.*")
						}
					}
				}
				response {
					status 200
				}
			}
		then:
			def e = thrown(IllegalStateException)
			e.message.contains "Query parameter 'age' can't be of a matching type: NOT_MATCHING for the server side"
	}

	def "should not allow query parameter with a different absent variation for server/client"() {
		when:
			org.springframework.cloud.contract.spec.Contract.make dsl
		then:
			def e = thrown(IllegalStateException)
			e.message.contains "Absent cannot only be used only on one side"
		where:
			dsl << [
					{
						request {
							method 'GET'
							urlPath("users") {
								queryParameters {
									parameter 'name': $(consumer(absent()), producer(""))
								}
							}
						}
						response {
							status 200
						}
					},
					{
						request {
							method 'GET'
							urlPath("users") {
								queryParameters {
									parameter 'name': $(consumer(""), producer(absent()))
								}
							}
						}
						response {
							status 200
						}
					},
					{
						request {
							method 'GET'
							urlPath("users") {
								queryParameters {
									parameter 'name': $(consumer(absent()), producer(matching("abc")))
								}
							}
						}
						response {
							status 200
						}
					}
			]
	}

	def "should generate request with url and queryParameters for client side"() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'GET'
					url($(consumer(regex(/users\/[0-9]*/)), producer("users/123"))) {
						queryParameters {
							parameter 'age': $(consumer(notMatching("^\\w*\$")), producer(10))
							parameter 'name': $(consumer(matching("Denis.*")), producer("Denis"))
						}
					}
				}
				response {
					status 200
				}
			}
		when:
			def json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
				{
					"request": {
						"method": "GET",
						"urlPattern": "users/[0-9]*",
						"queryParameters": {
						  "age": {
							"doesNotMatch": "^\\\\w*$"
						  },
						  "name": {
							"matches": "Denis.*"
						  }
						}
					},
					"response": {
						"status": 200,
					}
				}
				'''), json)
		and:
			stubMappingIsValidWireMockStub(json)
	}

	def 'should convert groovy dsl stub with rich tree Body as String to wireMock stub for the client side'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('GET')
					url $(consumer(~/\/[0-9]{2}/), producer('/12'))
					body """\
						{
						  "personalId": "${value(consumer(regex('[0-9]{11}')), producer('57593728525'))}",
						  "firstName": "${value(consumer(regex('.*')), producer('Bruce'))}",
						  "lastName": "${value(consumer(regex('.*')), producer('Lee'))}",
						  "birthDate": "${value(consumer(regex('[0-9]{4}-[0-9]{2}-[0-9]{2}')), producer('1985-12-12'))}",
						  "errors": [
									{
									  "propertyName": "${value(consumer(regex('[0-9]{2}')), producer('04'))}",
									  "providerValue": "Test"
									},
									{
									  "propertyName": "${value(consumer(regex('[0-9]{2}')), producer('08'))}",
									  "providerValue": "Test"
									}
								  ]
						}
						"""
				}
				response {
					status 200
					body("""\
								{
									"name": "Jan"
								}
							"""
					)
					headers {
						header 'Content-Type': 'text/plain'
					}
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
{
  "request" : {
	"urlPattern" : "/[0-9]{2}",
	"method" : "GET",
	"bodyPatterns" : [ {
	  "matchesJsonPath" : "$.errors[*][?(@.propertyName =~ /[0-9]{2}/)]"
	}, {
	  "matchesJsonPath" : "$.errors[*][?(@.providerValue == 'Test')]"
	}, {
	  "matchesJsonPath" : "$[?(@.lastName =~ /.*/)]"
	}, {
	  "matchesJsonPath" : "$[?(@.firstName =~ /.*/)]"
	}, {
	  "matchesJsonPath" : "$[?(@.birthDate =~ /[0-9]{4}-[0-9]{2}-[0-9]{2}/)]"
	}, {
	  "matchesJsonPath" : "$[?(@.personalId =~ /[0-9]{11}/)]"
	}]
  },
  "response" : {
	"status" : 200,
	"body" : "{\\"name\\":\\"Jan\\"}",
	"headers" : {
	  "Content-Type" : "text/plain"
	}
  }
}
			'''), wireMockStub)
	}

	def 'should use regexp matches when request body match is defined using a map with a pattern'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method 'POST'
					url '/reissue-payment-order'
					body(
							loanNumber: "999997001",
							amount: value(consumer(regex('[0-9.]+')), producer('100.00')),
							currency: "DKK",
							applicationName: value(consumer(regex('.*')), producer("Auto-Repayments")),
							username: value(consumer(regex('.*')), producer("scheduler")),
							cardId: 1
					)
				}
				response {
					status 200
					body '''
						{
						"status": "OK"
						}
					'''
					headers {
						header 'Content-Type': 'application/json'
					}
				}
			}
		when:
			def json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
{
  "request" : {
	"url" : "/reissue-payment-order",
	"method" : "POST",
	"bodyPatterns" : [ {
	  "matchesJsonPath" : "$[?(@.loanNumber == '999997001')]"
	}, {
	  "matchesJsonPath" : "$[?(@.username =~ /.*/)]"
	}, {
	  "matchesJsonPath" : "$[?(@.amount =~ /[0-9.]+/)]"
	}, {
	  "matchesJsonPath" : "$[?(@.cardId == 1)]"
	}, {
	  "matchesJsonPath" : "$[?(@.currency == 'DKK')]"
	}, {
	  "matchesJsonPath" : "$[?(@.applicationName =~ /.*/)]"
	} ]
  },
  "response" : {
	"status" : 200,
	"body" : "{\\"status\\":\\"OK\\"}",
	"headers" : {
	  "Content-Type" : "application/json"
	}
  }
}
				'''), json)
	}

	def "should generate stub for empty body"() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('POST')
					url("test")
					body("")
				}
				response {
					status 406
				}
			}
		when:
			def json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
			{
		"request": {
		  "method": "POST",
		  "url": "test",
		  "bodyPatterns": [
			{
				"equalTo": ""
			}
		  ]
		},
		"response": {
		  "status": 406
		}
			}
'''), json)
	}

	def "should generate stub with priority"() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				priority 9
				request {
					method('POST')
					url("test")
				}
				response {
					status 406
				}
			}
		when:
			def json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
				{
					"priority": 9,
					"request": {
						"method": "POST",
						"url": "test"
					},
					"response": {
						"status": 406
					}
				}
			'''), json)
	}

	@Issue("#127")
	def 'should use "test" as an alias for "server"'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('POST')
					url("foo")
					body(
							property: value(consumer("value"), producer("value"))
					)
				}
				response {
					status 200
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
{
  "request" : {
	"method" : "POST",
	"bodyPatterns" : [ {
	  "matchesJsonPath" : "$[?(@.property == 'value')]"
	} ]
  },
  "response" : {
	"status" : 200
  }
}
			'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	@Issue("#121")
	def 'should generate stub with empty list as a value of a field'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method('POST')
					url("foo")
					body(
							values: []
					)
				}
				response {
					status 200
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
				{
					"request": {
						"method": "POST",
						"bodyPatterns": [
							{
								"equalToJson": "{\\"values\\":[]}"
							}
						]
					},
					"response": {
						"status": 200
					}
				}
			'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	def 'should generate stub properly resolving GString with regular expression'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
				priority 1
				request {
					method 'POST'
					url '/users/password'
					headers {
						header 'Content-Type': 'application/json'
					}
					body(
							email: $(consumer(regex(email())), producer('not.existing@user.com')),
							callback_url: $(consumer(regex(hostname())), producer('http://partners.com'))
					)
				}
				response {
					status 404
					headers {
						header 'Content-Type': 'application/json'
					}
					body(
							code: 4,
							message: "User not found by email = [${value(producer(regex(email())), consumer('not.existing@user.com'))}]"
					)
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
		{
		  "request" : {
			"url" : "/users/password",
			"method" : "POST",
			"bodyPatterns" : [ {
			  "matchesJsonPath" : "$[?(@.callback_url =~ /((http[s]?|ftp):\\\\/)\\\\/?([^:\\\\/\\\\s]+)(:[0-9]{1,5})?/)]"
			}, {
			  "matchesJsonPath" : "$[?(@.email =~ /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,4}/)]"
			} ],
			"headers" : {
			  "Content-Type" : {
				"equalTo" : "application/json"
			  }
			}
		  },
		  "response" : {
			"status" : 404,
			"body" : "{\\"code\\":4,\\"message\\":\\"User not found by email = [not.existing@user.com]\\"}",
			"headers" : {
			  "Content-Type" : "application/json"
			}
		  },
		  "priority" : 1
		}
			'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	def 'should generate stub properly resolving GString with regular expression in url'() {
		given:
			org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {

				request {
					method 'PUT'
					url "/partners/${value(consumer(regex('^[0-9]*$')), producer('11'))}/agents/11/customers/09665703Z"
					headers {
						header 'Content-Type': 'application/json'
					}
					body(
							first_name: 'Josef',
					)
				}
				response {
					status 422
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
		{
		  "request" : {
			"urlPattern" : "/partners/^[0-9]*$/agents/11/customers/09665703Z",
			"method" : "PUT",
			"bodyPatterns" : [ {
			  "matchesJsonPath" : "$[?(@.first_name == 'Josef')]"
			} ],
			"headers" : {
			  "Content-Type" : {
				"equalTo" : "application/json"
			  }
			}
		  },
		  "response" : {
			"status" : 422
		  }
		}
			'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	@Issue('42')
	def 'should generate stub without optional parameters'() {
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), contractDsl).toWireMockClientStub()
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
			{
			  "request" : {
				"url" : "/users/password",
				"method" : "POST",
				"bodyPatterns" : [ {
				  "matchesJsonPath" : "$[?(@.callback_url =~ /((http[s]?|ftp):\\\\/)\\\\/?([^:\\\\/\\\\s]+)(:[0-9]{1,5})?/)]"
				}, {
				  "matchesJsonPath" : "$[?(@.email =~ /([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,4})?/)]"
				} ],
				"headers" : {
				  "Content-Type" : {
					"equalTo" : "application/json"
				  }
				}
			  },
			  "response" : {
				"status" : 404,
				"body" : "{\\"code\\":\\"123123\\",\\"message\\":\\"User not found by email = [not.existing@user.com]\\"}",
				"headers" : {
				  "Content-Type" : "application/json"
				}
			  },
			  "priority" : 1
			}
			'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
		where:
		contractDsl << [
				org.springframework.cloud.contract.spec.Contract.make {
					priority 1
					request {
						method 'POST'
						url '/users/password'
						headers {
							header 'Content-Type': 'application/json'
						}
						body(
								email: $(consumer(optional(regex(email()))), producer('abc@abc.com')),
								callback_url: $(consumer(regex(hostname())), producer('http://partners.com'))
						)
					}
					response {
						status 404
						headers {
							header 'Content-Type': 'application/json'
						}
						body(
								code: $(consumer("123123"), producer(optional("123123"))),
								message: "User not found by email = [${value(producer(regex(email())), consumer('not.existing@user.com'))}]"
						)
					}
				},
				org.springframework.cloud.contract.spec.Contract.make {
					priority 1
					request {
						method 'POST'
						url '/users/password'
						headers {
							header 'Content-Type': 'application/json'
						}
						body(
								""" {
								"email" : "${value(consumer(optional(regex(email()))), producer('abc@abc.com'))}",
								"callback_url" : "${value(consumer(regex(hostname())), producer('http://partners.com'))}"
								}
							"""
						)
					}
					response {
						status 404
						headers {
							header 'Content-Type': 'application/json'
						}
						body(
								""" {
								"code" : "${value(consumer(123123), producer(optional(123123)))}",
								"message" : "User not found by email = [${value(producer(regex(email())), consumer('not.existing@user.com'))}]"
								}
							"""
						)
					}
				}
		]
	}

	String toJsonString(value) {
		new JsonBuilder(value).toPrettyString()
	}

	Object parseJson(json) {
		new JsonSlurper().parseText(json)
	}

	String toWireMockClientJsonStub(groovyDsl) {
		new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), groovyDsl).toWireMockClientStub()
	}

	@Issue('180')
	def 'should generate stub with multipart parameters'() {
		given:
			org.springframework.cloud.contract.spec.Contract contractDsl = org.springframework.cloud.contract.spec.Contract.make {
				request {
					method "PUT"
					url "/multipart"
					multipart(
							formParameter: value(consumer(regex('".+"')), producer('"formParameterValue"')),
							someBooleanParameter: value(consumer(regex('(true|false)')), producer('true')),
							file: named(
									name: value(consumer(regex('.+')), producer('filename.csv')),
									content: value(consumer(regex('.+')), producer('file content')))
					)
				}
				response {
					status 200
				}
			}
		when:
			String wireMockStub = new WireMockStubStrategy("Test", new ContractMetadata(null, false, 0, null), contractDsl).toWireMockClientStub()
		then:
			println wireMockStub
			AssertionUtil.assertThatJsonsAreEqual(('''
		{
		  "request" : {
			"url" : "/multipart",
			"method" : "PUT",
			"bodyPatterns" : [ {
				"matches" : ".*--(.*)\\r\\nContent-Disposition: form-data; name=\\"formParameter\\"\\r\\n(Content-Type: .*\\r\\n)?(Content-Length: \\\\d+\\r\\n)?\\r\\n\\".+\\"\\r\\n--\\\\1.*"
    		}, {
      			"matches" : ".*--(.*)\\r\\nContent-Disposition: form-data; name=\\"someBooleanParameter\\"\\r\\n(Content-Type: .*\\r\\n)?(Content-Length: \\\\d+\\r\\n)?\\r\\n(true|false)\\r\\n--\\\\1.*"
    		}, {			
			  "matches" : ".*--(.*)\\r\\nContent-Disposition: form-data; name=\\"file\\"; filename=\\".+\\"\\r\\n(Content-Type: .*\\r\\n)?(Content-Length: \\\\d+\\r\\n)?\\r\\n.+\\r\\n--\\\\1.*"
			} ]
		  },
		  "response" : {
			"status" : 200
		  }
		}
			'''), wireMockStub)
		and:
			stubMappingIsValidWireMockStub(wireMockStub)
	}

	@Issue('#219')
	def "should generate request with an optional queryParameter for client side"() {
		given:
		org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
			request {
				method 'GET'
				urlPath ('/some/api') {
					queryParameters {
						parameter 'size': value(
								consumer(regex('[0-9]+')),
								producer(1)
						)
						parameter 'page': value(
								consumer(regex('[0-9]+')),
								producer(0)
						)
						parameter sort: value(
							consumer(optional(regex('^[a-z]+$'))),
							producer('id')
						)
					}
				}
			}
			response {
				status 200
				body(
						content: [[
										  id      : '00000000-0000-0000-0000-000000000000',
										  type  : 'Extraordinary',
										  state : 'ACTIVE',
								  ]],
						totalPages: 1,
						totalElements: 1,
						last: true,
						sort: [[
									   direction: 'ASC',
									   property: 'id',
									   ignoreCase: false,
									   nullHandling: 'NATIVE',
									   ascending: true
							   ]],
						first: true,
						numberOfElements: 1,
						size: 1,
						number: 0
				)
			}
		}
		when:
		def json = toWireMockClientJsonStub(groovyDsl)
		then:
		AssertionUtil.assertThatJsonsAreEqual(('''
				{
				  "request" : {
					"urlPath" : "/some/api",
					"method" : "GET",
					"queryParameters" : {
					  "size" : {
						"matches" : "[0-9]+"
					  },
					  "page" : {
						"matches" : "[0-9]+"
					  },
					  "sort" : {
						"matches" : "(^[a-z]+$)?"
					  }
					}
				  },
				  "response" : {
					"status" : 200,
					"body" : "{\\"content\\":[{\\"id\\":\\"00000000-0000-0000-0000-000000000000\\",\\"type\\":\\"Extraordinary\\",\\"state\\":\\"ACTIVE\\"}],\\"totalPages\\":1,\\"totalElements\\":1,\\"last\\":true,\\"sort\\":[{\\"direction\\":\\"ASC\\",\\"property\\":\\"id\\",\\"ignoreCase\\":false,\\"nullHandling\\":\\"NATIVE\\",\\"ascending\\":true}],\\"first\\":true,\\"numberOfElements\\":1,\\"size\\":1,\\"number\\":0}"
				  }
				}
				'''), json)
		and:
		stubMappingIsValidWireMockStub(json)
	}

	@Issue('#30')
	def "should not create a stub for a skipped contract"() {
		given:
		org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
			request {
				ignored()
				method 'GET'
				urlPath ('/some/api') {
					queryParameters {
						parameter 'size': value(
								consumer(regex('[0-9]+')),
								producer(1)
						)
					}
				}
			}
			response {
				status 200
				body('')
			}
		}
		when:
			def json = toWireMockClientJsonStub(groovyDsl)
		then:
			json == ''
	}

	@Issue('#30')
	def "should not create a stub for a contract matching ignored pattern"() {
		given:
		org.springframework.cloud.contract.spec.Contract groovyDsl = org.springframework.cloud.contract.spec.Contract.make {
			request {
				method 'GET'
				urlPath ('/some/api') {
					queryParameters {
						parameter 'size': value(
								consumer(regex('[0-9]+')),
								producer(1)
						)
					}
				}
			}
			response {
				status 200
				body('')
			}
		}
		when:
			def json = new WireMockStubStrategy("Test", new ContractMetadata(null, true, 0, null), groovyDsl).toWireMockClientStub()
		then:
			json == ''
	}
}
