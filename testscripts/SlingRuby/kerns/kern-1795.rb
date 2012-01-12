#!/usr/bin/env ruby


require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/users'
require 'nakamura/contacts'
require 'test/unit.rb'
include SlingUsers
include SlingFile
include SlingContacts

class TC_Kern1795Test < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @fm = FileManager.new(@s)
    @um = UserManager.new(@s)
    @cm = ContactManager.new(@s)
    m = uniqueness()
    @test_user1 = create_user "test_user1_#{m}", "Test", "User1"
    @test_user2 = create_user "test_user2_#{m}", "Test", "User2"
    @test_group = create_group "g-test_group_#{m}", "Test Group"
  end

  def test_add_user_to_and_remove_user_from_group
    @s.switch_user(@test_user1)
    res = @s.execute_get(@s.url_for("/system/me.json"))
    @log.info("/system/me response #{res.inspect}")
    assert_equal("200", res.code, "Me servlet should return successfully")
    me = JSON.parse(res.body)
    counts = me['profile']['counts']
    @log.info("test_add_user_to_and_remove_user_from_group initial user counts are: #{counts.inspect}")
    assert_equal(true, counts['contentCount'].nil? || counts['contentCount'] == 0)
    assert_equal(true, counts['membershipsCount'].nil? || counts['membershipsCount'] == 0)
    assert_equal(true, counts['contactsCount'].nil? || counts['contactsCount'] == 0)
  
    @s.switch_user(User.admin_user)
    @s.execute_post("#{@s.url_for(Group.url_for(@test_group.name))}.update.html", {":member" => @test_user1.name})
    wait_for_indexer
    @s.switch_user(@test_user1)
    res = @s.execute_get(@s.url_for("/system/me.json"))
    @log.info("after group.add_member /system/me response #{res.inspect}")
    assert_equal("200", res.code, "Me servlet should return successfully")
    me = JSON.parse(res.body)
    counts = me['profile']['counts']
    @log.info("after group.add_member user counts are: #{counts.inspect}")
    assert_equal(1, counts['membershipsCount'])
  
    @s.switch_user(User.admin_user)
    @s.execute_post("#{@s.url_for(Group.url_for(@test_group.name))}.update.html", {":member@Delete" => @test_user1.name})
    wait_for_indexer
    @s.switch_user(@test_user1)
    res = @s.execute_get(@s.url_for("/system/me.json"))
    @log.info("after group.remove_member /system/me response #{res.inspect}")
    assert_equal("200", res.code, "Me servlet should return successfully")
  
    me = JSON.parse(res.body)
    counts = me['profile']['counts']
    @log.info("after group.remove_member user counts are: #{counts.inspect}")
    assert_equal(0, counts['membershipsCount'])
  end

  # see KERN-1003.rb
  def test_and_and_delete_content_counts
    @s.switch_user(@test_user1)
    res = @s.execute_get(@s.url_for("/system/me.json"))
    assert_equal("200", res.code, "Me servlet should return successfully")
    me = JSON.parse(res.body)
    counts = me['profile']['counts']
    @log.info("test_and_and_delete_content_counts() user counts are: #{counts.inspect}")
    assert_equal(true, counts['contentCount'].nil? || counts['contentCount'] == 0)
    assert_equal(true, counts['membershipsCount'].nil? || counts['membershipsCount'] == 0)
    assert_equal(true, counts['contactsCount'].nil? || counts['contactsCount'] == 0)
  
    # test uploading a file
    res = @fm.upload_pooled_file('random.txt', 'This is some random content that should be stored in the pooled content area.', 'text/plain')
    # since wait_for_indexer creates pool content to use as a monitor
    # it creates side effects that cause this test to fail
    # wait_for_indexer
    sleep(5)
    assert_equal("201", res.code, "should be able to upload content")
    file = JSON.parse(res.body)
    id = file['random.txt']['poolId']
    url = @fm.url_for_pooled_file(id)
    res = @s.execute_get(@s.url_for("/system/me.json"))
    assert_equal("200", res.code, "Me servlet should return successfully")
    me = JSON.parse(res.body)
    counts = me['profile']['counts']
    @log.info("after fm.upload_pooled_file user counts are: #{counts.inspect}")
    assert_equal(1, counts['contentCount'], 'contentCount should be 1 after 1 upload')
  
    # test deleting the file
    res = @s.execute_post(url, {":operation" => "delete"})
    assert_equal(200, res.code.to_i, "Expected to be able to delete the file.")
    #wait_for_indexer #this was a solr delete
    sleep(5)
    res = @s.execute_get(@s.url_for("/system/me.json"))
    @log.info("/system/me response #{res.inspect}")
    assert_equal("200", res.code, "Me servlet should return successfully")
  
    me = JSON.parse(res.body)
    counts = me['profile']['counts']
    @log.info("user counts are: #{counts.inspect}")
    assert_equal(1, counts['contentCount'], 'contentCount should be 1 after 1 upload')
  end
  
  def test_add_contact_for_user
    @s.switch_user(@test_user1)
    res = @s.execute_get(@s.url_for("/system/me.json"))
    @log.info("test_add_contact_for_user() /system/me response #{res.inspect}")
    assert_equal("200", res.code, "Me servlet should return successfully")
    me = JSON.parse(res.body)
    counts = me['profile']['counts']
    @log.info("test_add_contact_for_user initial user counts are: #{counts.inspect}")
    assert_equal(true, counts['contentCount'].nil? || counts['contentCount'] == 0)
    assert_equal(true, counts['membershipsCount'].nil? || counts['membershipsCount'] == 0)
    assert_equal(true, counts['contactsCount'].nil? || counts['contactsCount'] == 0)
  
    create_connection(@test_user1, @test_user2)
    
    sleep(5)
  
    @s.switch_user(@test_user1)
    res = @s.execute_get(@s.url_for("/system/me.json"))
    @log.info("/system/me response #{res.inspect}")
    assert_equal("200", res.code, "Me servlet should return successfully")
    me = JSON.parse(res.body)
    counts = me['profile']['counts']
    @log.info("user counts are: #{counts.inspect}")
    assert_equal(1, counts['contactsCount'], 'contentsCount should be 1 after 1 invitation')
  
    #remove the contact
    res = @cm.remove_contact(@test_user2.name)
    @log.info("@cm.remove_contact() #{res.inspect}")
    wait_for_indexer
    res = @s.execute_get(@s.url_for("/system/me.json"))
    @log.info("/system/me response #{res.inspect}")
    assert_equal("200", res.code, "Me servlet should return successfully")
    me = JSON.parse(res.body)
    counts = me['profile']['counts']
    @log.info("user counts are: #{counts.inspect}")
    assert_equal(0, counts['contactsCount'], 'contentCount should be 0 after 1 removal')
  end

  def create_connection(baseUser, otherUser)
    @s.switch_user(baseUser)
    @cm.invite_contact(otherUser.name, "follower")
    @s.switch_user(otherUser)
    @cm.accept_contact(baseUser.name)
  end

  def teardown
    @s.switch_user(User.admin_user)
    super
  end
end

