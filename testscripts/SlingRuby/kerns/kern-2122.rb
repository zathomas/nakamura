#!/usr/bin/env ruby
# Add all files in testscripts\SlingRuby\lib directory to ruby "require" search path
require 'ruby-lib-dir.rb'

require 'sling/test'
require 'sling/file'
require 'sling/users'
require 'test/unit.rb'
include SlingUsers
include SlingFile

class TC_Kern2122 < Test::Unit::TestCase
  include SlingTest


  def setup
    super
    @fm = FileManager.new(@s)
    @um = UserManager.new(@s)
  end
  
  def test_add_and_remove_viewers
    m = Time.now.to_i.to_s

    # Create some users
    manager = create_user("manager-#{m}")
    viewer1 = create_user("viewer1-#{m}")
    viewer2 = create_user("viewer2-#{m}")

    @s.switch_user(manager)
    content = Time.now.to_f
    name = "random-#{content}.txt"
    fileBody = "Add the time to make it sort of random #{Time.now.to_f}."
    upload_res = @fm.upload_pooled_file(name, fileBody, 'text/plain')
    json = JSON.parse(upload_res.body)
    file_id = json[name]["poolId"]

    wait_for_indexer()
    find_res = @s.execute_get(@s.url_for("/var/search/pool/me/manager-all.tidy.json"))
    assert_equal("200",find_res.code,find_res.body)

    add_viewers_res = @fm.manage_members(file_id, [viewer1.name, viewer2.name], nil, nil, nil)
    assert_equal("200",add_viewers_res.code, "manage_members add viewers POST should have succeeded")
    members_res = @fm.get_members file_id
    json = JSON.parse(members_res.body)
    assert_equal("200",members_res.code, "get_members GET should have succeeded")
    viewers = json["viewers"]
    assert_equal(2, viewers.length, "should be 2 viewers added by manager" )
    
    # now have viewer1 try to remove viewer2
    @s.switch_user(viewer1)
    remove_viewer2_res = @fm.manage_members(file_id, nil, [viewer2.name], nil, nil)
    members_res = @fm.get_members file_id
    json = JSON.parse(members_res.body)
    assert_equal("200",members_res.code)
    viewers = json["viewers"]
    assert_equal(2, viewers.length, "should be 2 viewers because viewer1 cannot remove viewer2" )
    
    # now have viewer1 remove themself
    # this is how to remove an item from a user's library
    remove_viewer1_res = @fm.manage_members(file_id, nil, [viewer1.name], nil, nil)
    assert_equal("200",remove_viewer1_res.code, "expected viewer1 to be able to remove themself")
    
    # need to switch to viewer2 to retrieve members as viewer1 cannot now do it having been removed
    @s.switch_user(viewer2)
    members_res = @fm.get_members file_id
    assert_equal("200",members_res.code)    
    json = JSON.parse(members_res.body)
    viewers = json["viewers"]
    assert_equal(1, viewers.length, "should be 1 viewer left because viewer1 can remove themself from viewers" )
    remaining_viewer = viewers[0]
    assert_equal(viewer2.name, remaining_viewer["userid"], "should be viewer2 remaining after removal of viewer1" )
  end
    
end
