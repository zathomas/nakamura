#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/users'
require 'nakamura/contacts'
include SlingUsers
include SlingFile
include SlingContacts

class TC_Kern1874Test < Test::Unit::TestCase
  include SlingTest
  TEST_SAKAI_DOC_MIME_TYPE = "x-sakai/document"
  
  @created_doc_path = nil
  def setup
    super
    m = uniqueness()
    @test_user1 = create_user "test-user1-#{m}", "Test", "User1"
    @test_sakai_doc_name = "test-sakai-doc-" + uniqueness()
  end
  
  
  def test_create_find_delete_sakai_doc
    # create a sakai doc with custom mimetype
    post_params = {}
    post_params["sakai:pooled-content-file-name"] = @test_sakai_doc_name
    post_params["mimeType"] = TEST_SAKAI_DOC_MIME_TYPE
    post_params["sakai:description"] = @test_sakai_doc_name + "description"
    post_params["sakai:permissions"] = "public"
    post_params["sakai:copyright"] = "creativecommons"
    post_params["_charset_"] = "utf-8"
    res = @s.execute_post(@s.url_for("/system/pool/createfile"), post_params)
    wait_for_indexer
    @log.info("response from creating doc: #{res.inspect}")
    assert_equal("201", res.code, "Expecting 201 on creating doc")
    json = JSON.parse(res.body)
    assert_not_nil(json, "Expecting valid json from doc creation")
    content_item = json['_contentItem']
    mime_type = content_item["item"]["_mimeType"]
    assert_equal(TEST_SAKAI_DOC_MIME_TYPE, mime_type, "Expecting valid mime type from response")
    doc_name = content_item["item"]["sakai:pooled-content-file-name"]
    assert_equal(@test_sakai_doc_name, doc_name, "Expecting valid file name from response")
    @created_doc_path = "/p/" + content_item["poolId"]
    assert_not_nil(@created_doc_path, "Expecting valid doc path from response")
    
    #now find the doc with first specified query in KERN-1874
    query_params = {}
    query_params["q"] = TEST_SAKAI_DOC_MIME_TYPE
    query_params["items"] = "10"
    # find_and_evaluate_doc query_params
    
    #now find the doc with first specified query in KERN-1874
    query_params = {}
    query_params["q"] = "*"
    query_params["mimetype"] = TEST_SAKAI_DOC_MIME_TYPE
    query_params["items"] = "10"
    # find_and_evaluate_doc query_params
    
    # now delete the created doc
    delete_params = {}
    delete_params[":operation"] = "delete"
    res = @s.execute_post(@s.url_for(@created_doc_path), delete_params)
    @log.info("response from deleting doc: #{res.code}")
    assert_equal("200", res.code, "Expecting 200 on deleting doc")
    
    #now confirm it cannot be found
    query_params = {}
    query_params["q"] = TEST_SAKAI_DOC_MIME_TYPE
    query_params["items"] = "10"
    
    @log.info("trying to find deleted doc with query params: #{query_params.inspect}")
    res = @s.execute_get(@s.url_for("/var/search/pool/all.infinity.json"), query_params)
    @log.info("response from trying to find deleted doc: #{res.inspect}")
    assert_equal("200", res.code, "Expecting 200 on finding doc")
    json = JSON.parse(res.body)
    assert_not_nil(json, "Expecting valid json from doc retrieval")
    results = json["results"]
    doc_found = false;
    results.each do |result|
      test_doc_name = result["sakai:pooled-content-file-name"]
      if (@test_sakai_doc_name.eql? test_doc_name)
        doc_found = true
      end
    end
    assert_equal(false, doc_found, "Expecting not to find deleted doc")
  end

  # This method will only work reliably on an empty repository.
  # If there are more than 10 publicly accessible documents, it will
  # likely report failure.
  # def find_and_evaluate_doc(query_params)
  #   @log.info("finding doc with query params: #{query_params.inspect}")
  #   res = @s.execute_get(@s.url_for("/var/search/pool/all.infinity.json"), query_params)
  #   @log.info("response from finding doc: #{res.inspect}, #{res.body}")
  #   json = JSON.parse(res.body)
  #   assert_not_nil(json, "Expecting valid json from doc retrieval")
  #   results = json["results"]
  #   doc_count = 0
  #   doc_found = false
  #   doc_name = nil
  #   doc_mime_type = nil
  #   results.each do |result|
  #     test_doc_name = result["sakai:pooled-content-file-name"]
  #     if (@test_sakai_doc_name.eql? test_doc_name)
  #          doc_name = test_doc_name
  #          doc_found = true
  #          doc_mime_type = result["_mimeType"]
  #          doc_count = doc_count +1
  #     end
  #   end
  #   assert_equal(true, doc_found, "Expecting to find #{@test_sakai_doc_name} with query: #{query_params.inspect}")
  #   assert_equal(@test_sakai_doc_name, doc_name, "Expecting to find created doc name from query response")
  #   assert_equal(TEST_SAKAI_DOC_MIME_TYPE, doc_mime_type, "Expecting to find doc mimetype from query response")
  #   assert_equal(1, doc_count, "Expecting to find just one created doc")
  # end

  def teardown
    @s.switch_user(User.admin_user)
    super
  end
end
