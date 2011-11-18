#!/usr/bin/env ruby


require 'nakamura'
require 'nakamura/test'
require 'nakamura/authz'
require 'test/unit.rb'

dir = File.dirname(__FILE__)
if not dir.match(/^\//)
  dir = "./#{dir}"
end

require "#{dir}/authz-base.rb"
include SlingInterface
include SlingUsers
include SlingAuthz

class TC_OwnerAuthZTest < Test::Unit::TestCase
  include AuthZBase
 

  def test_authzOwner
    @log.info("This Test is ignored since we no longer have the concept of owner")
    if ( false )
        @delete = false
	m = Time.now.to_i.to_s
	@authz = SlingAuthz::Authz.new(@s)
	path = "test/authztest/node"+m
	user1 = "user1-"+m
	user2 = "user2-"+m
	u1 = create_user(user1)
	u2 = create_user(user2)
	
        owner = @um.create_group("owner")
        if (owner == nil)
          # assume already exists
          owner = Group.new("owner")
        end
        #@s.debug = true
        @s.update_properties(owner, { "dynamic" => "true" })
        assert_not_nil(owner, "Expected owner group to be created")

	admin = SlingUsers::User.admin_user()
	
	@log.info("Creating Node at #{path}")
	create_node(path,"jcr:mixinTypes" => "mix:created", "testproperty" => "testvalue")
	
	updateAcl(path,user1,true,true) # allow u1 to write, so we can create the sub node
	checkAcl(path,user1,true,true)
	@s.switch_user(u1)
	# create a child node as u1
	childPath = path+"/childnode"
	create_node(path+"/childnode","jcr:mixinTypes" => "mix:created", "sakai:testchildproperty" => "testvalue")
	@s.switch_user(admin)
	deleteAcl(path,user1) # remove the user1 acl
	updateAcl(path, "everyone" ,true,false) # grant everyone read
	updateAcl(path, owner.name ,true,true) # grant owner write
	
	checkAcl(path,"everyone",true,false)
	checkAcl(path, owner.name, true, true)
	
	checkHttpAccess(childPath,u1," as owner of the child path "+childPath,true,true)
	checkHttpAccess(childPath,u2," as owner of the child path "+childPath,true,false)
      end
  end


end


