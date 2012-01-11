#!/usr/bin/env ruby


require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/users'
require 'test/unit.rb'
include SlingUsers
include SlingFile

class TC_Kern1045 < Test::Unit::TestCase
  include SlingTest


  def setup
    super
    @fm = FileManager.new(@s)
    @um = UserManager.new(@s)
  end


  def test_search_me
    m = uniqueness()

    # Create some users
    owner = create_user("creator2-#{m}")
    viewer = create_user("manager2-#{m}")
    groupuser = create_user("groupuser2-#{m}")

    @s.switch_user(owner)
    content = uniqueness()
    name = "random-#{content}.txt"
    fileBody = "Add the time to make it sort of random #{Time.now.to_f}."
    res = @fm.upload_pooled_file(name, fileBody, 'text/plain')
    json = JSON.parse(res.body)
    id = json[name]['poolId']

    sleep(5)

    # Search the files that I manage .. should be 1
    res = @s.execute_get(@s.url_for("/var/search/pool/me/manager-all.tidy.json"))
    assert_equal("200",res.code,res.body)
    json = JSON.parse(res.body)
    assert_equal(1, json["results"].length)
    assert_equal(fileBody.length ,json["results"][0]["_length"])

  end

end
