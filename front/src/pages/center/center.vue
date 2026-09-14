<template>
	<div class="center-preview">
		<div class="center-title">{{ title }}</div>
		<div class="center-info">
			<div class="center-info-title">个人信息</div>
			<div class="img-box" v-if="userTableName=='chezhu'">
				<img :src="sessionForm.touxiang?baseUrl + sessionForm.touxiang:require('@/assets/avator.png')">
			</div>
			<div class="img-box" v-if="userTableName=='weixiujishi'">
				<img :src="sessionForm.touxiang?baseUrl + sessionForm.touxiang:require('@/assets/avator.png')">
			</div>
			<div class="info-item1" v-if="userTableName=='chezhu'">
				<span class="icon iconfont "></span>
				<div class="label">账号</div>
				<div class="text">{{sessionForm.zhanghao}}</div>
			</div>
			<div class="info-item2" v-if="userTableName=='chezhu'">
				<span class="icon iconfont "></span>
				<div class="label">姓名</div>
				<div class="text">{{sessionForm.xingming}}</div>
			</div>
			<div class="info-item3" v-if="userTableName=='chezhu'">
				<span class="icon iconfont "></span>
				<div class="label">性别</div>
				<div class="text">{{sessionForm.xingbie}}</div>
			</div>
			<div class="info-item4" v-if="userTableName=='chezhu'">
				<span class="icon iconfont "></span>
				<div class="label">手机</div>
				<div class="text">{{sessionForm.shouji}}</div>
			</div>
		
		</div>
	
		<el-tabs v-model="activeCenterTab" class="center-tabs" tab-position="top" @tab-click="handleClick">
			<el-tab-pane label="个人中心" name="profile">
				<el-form class="center-preview-pv oc-form-section" ref="sessionForm" :model="sessionForm" :rules="rules" label-width="180px">
					<el-form-item class="center-item" v-if="userTableName=='chezhu'" label="账号" prop="zhanghao">
						<el-input v-model="sessionForm.zhanghao" placeholder="账号" readonly></el-input>
					</el-form-item>
					<el-form-item class="center-item" v-if="userTableName=='chezhu'" label="姓名" prop="xingming">
						<el-input v-model="sessionForm.xingming" placeholder="姓名" ></el-input>
					</el-form-item>
					<el-form-item class="center-item" v-if="userTableName=='chezhu'" label="性别" prop="xingbie">
						<el-select v-model="sessionForm.xingbie" placeholder="请选择性别" >
							<el-option v-for="(item, index) in dynamicProp.xingbie" :key="index" :label="item" :value="item"></el-option>
						</el-select>
					</el-form-item>
					<el-form-item class="center-item" v-if="userTableName=='chezhu'" label="手机" prop="shouji">
						<el-input v-model="sessionForm.shouji" placeholder="手机" ></el-input>
					</el-form-item>
					<el-form-item class="center-item" v-if="userTableName=='chezhu'" label="头像" prop="touxiang">
						<file-upload
							tip="点击上传头像"
							action="file/upload"
							:limit="1"
							:multiple="true"
							:fileUrls="sessionForm.touxiang?sessionForm.touxiang:''"
							@change="chezhutouxiangHandleAvatarSuccess"
							></file-upload>
					</el-form-item>
					<el-form-item class="center-btn-item">
						<el-button class="updateBtn" type="primary" @click="onSubmit('sessionForm')">
							<span class="icon iconfont "></span>
							<span class="text">更新信息</span>
						</el-button>
						<el-button class="closeBtn" type="danger" @click="logout">
							<span class="icon iconfont "></span>
							<span class="text">取消</span>
						</el-button>
					</el-form-item>
				</el-form>
			</el-tab-pane>
			<el-tab-pane label="修改密码" name="password">
				<el-form class="center-preview-pv oc-form-section" ref="passwordForm" :model="passwordForm" :rules="passwordRules" label-width="180px">
					<el-form-item class="center-item" label="原密码" prop="password">
						<el-input type="password" v-model="passwordForm.password" placeholder="原密码"></el-input>
					</el-form-item>
					<el-form-item class="center-item" label="新密码" prop="newpassword">
						<el-input type="password" v-model="passwordForm.newpassword" placeholder="新密码"></el-input>
					</el-form-item>
					<el-form-item class="center-item" label="确认密码" prop="repassword">
						<el-input type="password" v-model="passwordForm.repassword" placeholder="确认密码"></el-input>
					</el-form-item>
					<el-form-item class="center-btn-item">
						<el-button class="updateBtn" type="primary" @click="updatePassword">
							<span class="icon iconfont "></span>
							<span class="text">修改密码</span>
						</el-button>
					</el-form-item>
				</el-form>
			</el-tab-pane>
			<el-tab-pane label="聊天记录" name="chat" style="width: 100%;" v-if="changeHasChat()">
				<div class="z-box">
					<div class="section-content" v-for="item in recordList" :key="item.id" @click.stop="chatClick(item)">
						<div class="chat-left">
							<img :src="item.picture?baseUrl + item.picture:require('@/assets/avator.png')">
							<div class="chat-info">
								<div class="chat-name">{{item.name}}</div>
								<div class="chat-text">
									<div class="chat-noread" v-if="item.notreadnum">{{item.notreadnum}}</div>
									{{item.content.split('/').length>1&&item.content.split('/')[0]=='upload'?'[图片]':item.content}}
								</div>
							</div>
						</div>
						<div class="chat-btn">
							<div class="friendBtn2" @click.stop="chatDel(item)">删除</div>
						</div>
					</div>
					<div class="noList oc-empty" v-if="!recordList.length">
						暂无聊天记录
					</div>
				</div>
			
			</el-tab-pane>
			<el-tab-pane
				v-for="(item,index) in menuList"
				:key="index"
				v-if="hasBack(item.menu) && centerModuleComponent(item.child[0].tableName)"
				:label="item.child[0].menu"
				:name="item.child[0].tableName"
				lazy>
				<div class="center-module-pane">
					<component
						v-if="activeCenterTab === item.child[0].tableName"
						:is="centerModuleComponent(item.child[0].tableName)"
						:embedded="true" />
				</div>
			</el-tab-pane>
			<el-tab-pane label="我的收藏" name="storeup" lazy>
				<div class="center-module-pane">
					<storeup-list v-if="activeCenterTab === 'storeup'" :embedded="true" />
				</div>
			</el-tab-pane>
		</el-tabs>

		<el-dialog :visible.sync="chatVisible" @close="clearChat" :title="nowname">
			<div class="chat-content" id="chat-content">
				<div v-bind:key="item.id" v-for="item in chatList">
					<div v-if="item.addtime" class="addtime">{{timeFormat(item.addtime)}}</div>
					<div v-if="item.uid==sessionForm.id" class="right-content">
						<el-alert v-if="item.format==1" class="text-content" :title="item.content" :closable="false"
							type="warning"></el-alert>
						<el-image v-else fit="cover" :src="item.content?baseUrl + item.content:''" style="width: 100px;height: 100px;" :preview-src-list="[item.content?baseUrl + item.content:'']"></el-image>
						<img :src="mypic?baseUrl + mypic:require('@/assets/avator.png')" alt="" style="width: 30px;border-radius: 50%;height: 30px;margin: 0 0 0 10px;" />
					</div>
					<div v-else class="left-content">
						<img :src="nowfpic?baseUrl + nowfpic:require('@/assets/avator.png')" alt="" style="width: 30px;border-radius: 50%;height: 30px;margin: 0 10px 0 0;" />
						<el-alert v-if="item.format==1" class="text-content" :title="item.content" :closable="false"
							type="success"></el-alert>
						<el-image v-else fit="cover" :src="item.content?baseUrl + item.content:''" style="width: 100px;height: 100px;" :preview-src-list="[item.content?baseUrl + item.content:'']"></el-image>
					</div>
					<div class="clear-float"></div>
				</div>
			</div>
			<div slot="footer" class="dialog-footer">
				<el-input @keydown.enter.native="addChat(null)" v-model="chatForm.content" placeholder="请输入内容" style="width: calc(100% - 180px);float: left;">
				</el-input>
				<el-button :disabled="chatForm.content?false:true" type="primary" @click="addChat(null)">发送</el-button>
				<el-upload style="display: inline-block;margin: 0 0 0 6px;" class="upload-demo" :action="uploadUrl" :on-success="uploadSuccess"
					:show-file-list="false">
					<el-button type="success">上传图片</el-button>
				</el-upload>
			</div>
		</el-dialog>
	</div>
</template>

<script>
	import config from '@/config/config';
	import menu from '@/config/menu';
	import Vue from 'vue';
	import timeMethod from '@/common/timeMethod'
	import AppointmentList from '@/pages/fuwuyuyue/list.vue'
	import ServiceRecordList from '@/pages/weixiujilu/list.vue'
	import ServiceReviewList from '@/pages/pingjiafankui/list.vue'
	import StoreupList from '@/pages/storeup/list.vue'
	import {
		WebsocketMixin
	} from '@/mixins/WebsocketMixin'
	export default {
		components: {
			AppointmentList,
			ServiceRecordList,
			ServiceReviewList,
			StoreupList,
		},
		mixins: [WebsocketMixin],
		//数据集合
		data() {
			return {
				title: '个人中心',
				activeCenterTab: 'profile',
				baseUrl: config.baseUrl,
				sessionForm: {},
				passwordForm: {},
				passwordRules: {
					password: [
						{
							required: true,
							message: "密码不能为空",
							trigger: "blur"
						}
					],
					newpassword: [
						{
							required: true,
							message: "新密码不能为空",
							trigger: "blur"
						}
					],
					repassword: [
						{
							required: true,
							message: "确认密码不能为空",
							trigger: "blur"
						}
					]
				},
				rules: {},
				menuList: [],
				disabled: false,
				uploadUrl: config.baseUrl + 'file/upload',
				imageUrl: '',
				headers: {Token: localStorage.getItem('frontToken')},
				userTableName: localStorage.getItem('UserTableName'),
				dynamicProp: {},
				recordList: [],
				chatVisible: false,
				nowfid: 0,
				nowfpic:'',
				nowname: '',
				mypic: localStorage.getItem('frontHeadportrait'),
				chatList: [],
				chatForm: {
					content: ''
				},
				hasChatList: [
					'chezhu',
					'weixiujishi',
					'shouhoufuwu',
				],
			}
		},
		created() {
			let menus = menu.list()
			for(let x in menus){
				if(menus[x].tableName == this.userTableName){
					for(let i in menus[x].backMenu){
						if(menus[x].backMenu[i].menu=='考试管理'){
							menus[x].backMenu.splice(i,1)
						}
					}
					this.menuList = menus[x].backMenu
				}
			}
			if ('chezhu' == this.userTableName) {
				this.$set(this.sessionForm, 'zhanghao', null);
			}
			if ('chezhu' == this.userTableName) {
				this.$set(this.sessionForm, 'mima', null);
			}
			if ('chezhu' == this.userTableName) {
				this.$set(this.sessionForm, 'xingming', null);
			}
			if ('chezhu' == this.userTableName) {
				this.$set(this.sessionForm, 'xingbie', null);
			}
			if ('chezhu' == this.userTableName) {
				this.$set(this.sessionForm, 'shouji', null);
			}
			if ('chezhu' == this.userTableName) {
				this.$set(this.sessionForm, 'touxiang', null);
			}

			if ('chezhu' == this.userTableName&&this.rules['zhanghao']){
				this.rules['zhanghao'].push({ required: true, message: '请输入账号', trigger: 'blur' })
			}else if('chezhu' == this.userTableName&&!this.rules['zhanghao']) {
				this.$set(this.rules, 'zhanghao', [{ required: true, message: '请输入账号', trigger: 'blur' }]);
			}
			if ('chezhu' == this.userTableName&&this.rules['mima']){
				this.rules['mima'].push({ required: true, message: '请输入密码', trigger: 'blur' })
			}else if('chezhu' == this.userTableName&&!this.rules['mima']) {
				this.$set(this.rules, 'mima', [{ required: true, message: '请输入密码', trigger: 'blur' }]);
			}
			if ('chezhu' == this.userTableName&&this.rules['xingming']){
				this.rules['xingming'].push({ required: true, message: '请输入姓名', trigger: 'blur' })
			}else if('chezhu' == this.userTableName&&!this.rules['xingming']) {
				this.$set(this.rules, 'xingming', [{ required: true, message: '请输入姓名', trigger: 'blur' }]);
			}
			if ('chezhu' == this.userTableName) {
				this.$set(this.rules, 'shouji', [{ required: false, validator: this.$validate.isMobile, trigger: 'blur' }]);
			}

			this.init();
			this.sessionForm = JSON.parse(localStorage.getItem('sessionForm'))
		},
		//方法集合
		methods: {
			init() {
				if ('chezhu' == this.userTableName) {
					this.dynamicProp.xingbie = '男,女'.split(',');
				}
			},
			setSession(){
				localStorage.setItem('sessionForm',JSON.stringify(this.sessionForm))
			},
			onSubmit(formName) {
				if(`chezhu` == this.userTableName && this.sessionForm.touxiang!=null){
					this.sessionForm.touxiang = this.sessionForm.touxiang.replace(new RegExp(this.$config.baseUrl,"g"),"");
				}
				this.$refs[formName].validate((valid) => {
					if (valid) {
						this.$http.post((localStorage.getItem('frontSessionTable') || 'account') + '/update', this.sessionForm).then(res => {
							if (res.data.code == 0) {
								this.setSession()
								this.$message({
									message: '更新成功',
									type: 'success',
									duration: 1500
								});
							}
						});
					} else {
						return false;
					}
				});
			},
			chezhutouxiangHandleAvatarSuccess(fileUrls) {
				this.sessionForm.touxiang = fileUrls;
			},
			handleClick(tab, event) {
				const label = event.target.outerText
				switch(label) {
					case '个人中心':
						break;
					case '修改密码':
						this.passwordForm = {
							password: '',
							newpassword: '',
							repassword: '',
						}
						this.$forceUpdate()
						break;
					case '聊天记录':
						this.getRecordList()
						break;
					case '我的收藏':
						localStorage.setItem('storeupType', 1);
						break;
				}

				this.title = label;
			},
			centerModuleComponent(tableName) {
				return {
					fuwuyuyue: 'appointment-list',
					weixiujilu: 'service-record-list',
					pingjiafankui: 'service-review-list',
				}[tableName] || null
			},
			changeHasChat(){
				let table = localStorage.getItem('frontSessionTable')
				for(let x in this.hasChatList){
					if(this.hasChatList[x]==table){
						return true
					}
				}
				return false
			},
			getRecordList() {
				this.$http.get('friend/page2', {
					params: {
						uid: Number(localStorage.getItem('frontUserid')),
						type: 2
					}
				}).then(res => {
					if (res.data && res.data.code == 0) {
						this.recordList = res.data.data.list
					}
				})
			},
			chatClick(row) {
				this.nowfid = row.fid
				this.nowfpic = row.picture
				this.nowname = row.name
				this.initWebSocket(this.nowfid)
				this.getChatList()
				this.chatVisible = true
			},
			chatDel(row){
				this.$confirm('是否删除聊天记录？').then(_ => {
					let arr = []
					let brr = []
					this.$http.get('chatmessage/list',{params: {
						page: 1,
						limit: 10000,
						uid: Number(localStorage.getItem('frontUserid')),
						fid: row.fid
					}}).then(rs=>{
						if(rs.data.data.list.length){
							for(let x in rs.data.data.list){
								arr.push(rs.data.data.list[x].id)
							}
						}
						this.$http.get('chatmessage/list',{params: {
							page: 1,
							limit: 10000,
							uid: row.fid,
							fid: Number(localStorage.getItem('frontUserid'))
						}}).then(rs2=>{
							if(rs2.data.data.list.length){
								for(let x in rs2.data.data.list){
									arr.push(rs2.data.data.list[x].id)
								}
							}
							if(arr.length){
								this.$http.post('chatmessage/delete',arr).then(()=>{})
							}
							this.$http.get('friend/list',{params: {
								page: 1,
								limit: 10000,
								uid: row.fid,
								fid: Number(localStorage.getItem('frontUserid')),
								type: 0
							}}).then(rs3=>{
								if(rs3.data.data.list.length){
									for(let x in rs3.data.data.list){
										brr.push(rs3.data.data.list[x].id)
									}
								}
								this.$http.get('friend/list',{params: {
									page: 1,
									limit: 10000,
									fid: row.fid,
									uid: Number(localStorage.getItem('frontUserid')),
									type: 0
								}}).then(rs4=>{
									if(rs4.data.data.list.length){
										for(let x in rs4.data.data.list){
											brr.push(rs4.data.data.list[x].id)
										}
									}
									this.$http.get('friend/list',{params: {
										page: 1,
										limit: 10000,
										fid: row.fid,
										uid: Number(localStorage.getItem('frontUserid')),
										type: 2
									}}).then(rs5=>{
										if(rs5.data.data.list.length){
											for(let x in rs5.data.data.list){
												brr.push(rs5.data.data.list[x].id)
											}
										}
										this.$http.get('friend/list',{params: {
											page: 1,
											limit: 10000,
											uid: row.fid,
											fid: Number(localStorage.getItem('frontUserid')),
											type: 2
										}}).then(rs6=>{
											if(rs6.data.data.list.length){
												for(let x in rs6.data.data.list){
													brr.push(rs6.data.data.list[x].id)
												}
											}
											this.$http.post('friend/delete',brr).then(()=>{
												this.$message.success('删除成功')
												this.getRecordList()
											})
										})
									})
								})
							})
						})
					})
				}).catch(_ => {});
			},
			websocketOnopen: function() {
				
			},
			websocketOnmessage:function(e) {
				this.getChatList()
			},
			getChatList() {
				this.$http.get('chatmessage/mlist', {
					params: {
						page: 1,
						limit: 1000,
						uid: Number(localStorage.getItem('frontUserid')),
						fid: this.nowfid
					}
				}).then(res => {
					if (res.data && res.data.code == 0) {
						this.chatList = this.formatMessages(res.data.data.list)
						let div = document.getElementsByClassName('chat-content')[0]
						setTimeout(() => {
							if (div)
								div.scrollTop = div.scrollHeight
						}, 0)
					}
				})
			},
			formatMessages(messages) {
				let lastTime = null;
				messages.forEach((message, index) => {
					const currentTime = new Date(message.addtime).getTime();
					if (lastTime !== null) {
						const timeDiff = (currentTime - lastTime) / 1000 / 60; // 转换为分钟
						if (timeDiff < 3) {
							message.addtime = ''; // 如果小于3分钟，不显示时间
						}
					}
					lastTime = currentTime;
				});
				return messages;
			},
			timeFormat(time) {
				const Time = timeMethod.getTime(time).split("T");
				//当前消息日期属于周
				const week = timeMethod.getDateToWeek(time);
				//当前日期0时
				const nti = timeMethod.setTimeZero(timeMethod.getNowTime());
				//消息日期当天0时
				const mnti = timeMethod.setTimeZero(timeMethod.getTime(time));
				//计算日期差值
				const diffDate = timeMethod.calculateTime(nti, mnti);
				//本周一日期0时 （后面+1是去除当天时间）
				const fwnti = timeMethod.setTimeZero(timeMethod.countDateStr(-timeMethod.getDateToWeek(timeMethod
					.getNowTime()).weekID + 1));
				//计算周日期差值
				const diffWeek = timeMethod.calculateTime(mnti, fwnti);

				if (diffDate === 0) { //消息发送日期减去当天日期如果等于0则是当天时间
					return Time[1].slice(0, 5);
				} else if (diffDate < 172800000) { //当前日期减去消息发送日期小于2天（172800000ms）则是昨天-  一天最大差值前天凌晨00:00:00到今天晚上23:59:59
					return "昨天 " + Time[1].slice(0, 5);
				} else if (diffWeek >= 0) { //消息日期减去本周一日期大于0则是本周
					return week.weekName;
				} else { //其他时间则是日期
					return Time[0].slice(5, 10);
				}
			},
			clearChat(){
				this.websocketOnclose();
				this.chatList = []
				this.getRecordList()
			},
			uploadSuccess(res) {
				if (res.code == 0) {
					this.addChat('upload/' + res.file);
				}
			},
			addChat(ask=null) {
				this.$http.post('chatmessage/add', {
					uid: Number(localStorage.getItem('frontUserid')),
					fid: this.nowfid,
					content: ask?ask:this.chatForm.content,
					format: ask?2:1
				}).then(res2 => {
					this.websocketSend(ask?ask:this.chatForm.content)
					this.chatForm = {
						content: ''
					}
					this.getChatList()
				})
			},
			async updatePassword(){
				this.$refs["passwordForm"].validate(async valid => {
					if (valid) {
						if (this.passwordForm.newpassword != this.passwordForm.repassword) {
							this.$message.error("两次密码输入不一致");
							return;
						}
						if (this.passwordForm.newpassword == this.passwordForm.password) {
							this.$message.error("新密码与原密码相同！");
							return;
						}
						this.$http.post('account/password', {
							oldPassword: this.passwordForm.password,
							newPassword: this.passwordForm.newpassword
						}).then(({data})=>{
							if (data && data.code === 0) {
								this.$message({
									message: "修改密码成功,下次登录系统生效",
									type: "success",
									duration: 1500,
									onClose: () => {
									}
								});
								this.setSession()
							} else {
								this.$message.error(data.msg);
							}
						});
					}
				})
			},
			logout() {
				localStorage.clear();
				Vue.http.headers.common['Token'] = "";
				this.$router.push('/index/home');
				this.activeIndex = '0'
				localStorage.setItem('keyPath', this.activeIndex)
				this.$forceUpdate()
				this.$message({
					message: '登出成功',
					type: 'success',
					duration: 1500,
				});
			},
			hasBack(name){
				switch(name){
					case '我的收藏管理':
						return false
						break;
					default:
						return true
				}
			}
		}
	}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
	.center-preview {
		margin: 10px auto;
		color: #666;
		background: #fff;
		display: flex;
		width: 1200px;
		font-size: 15px;
		align-items: flex-start;
		position: relative;
		flex-wrap: wrap;
		.center-title {
			padding: 0;
			margin: 20px 0;
			color: #333;
			background: none;
			width: 110%;
			font-size: 26px;
			border-color: #0063CD;
			border-width: 0 0 2px;
			line-height: 50px;
			border-style: solid;
			text-align: left;
			order: 0;
		}
		.center-info {
			padding: 0 0 20px;
			margin: 0px 0 0 50px;
			display: none;
			line-height: 2;
			flex-wrap: wrap;
			border-radius: 0;
			box-shadow: none;
			background: none;
			width: 300px;
			justify-content: center;
			align-items: center;
			height: auto;
			order: 2;
			.center-info-title {
				color: #fff;
				background: radial-gradient(circle, rgba(237,94,91,1) 0%, rgba(209,6,2,1) 100%);
				width: 100%;
				font-size: 16px;
				line-height: 50px;
				text-align: center;
				height: 50px;
			}
			.img-box {
				display: inline-block;
				width: 94%;
				font-size: 0;
				border-color: #efefef;
				border-width: 0 0 0px 0;
				border-style: solid;
				height: auto;
				img {
					border-radius: 4px;
					margin: 20px auto 0;
					object-fit: cover;
					display: block;
					width: 100%;
					border-color: #efefef;
					border-width: 0 0 1px 0;
					border-style: solid;
					height: 150px;
				}
			}
			.info-item1 {
				padding: 5px 0;
				display: flex;
				width: 100%;
				border-color: #ddd;
				border-width: 0 0 1px 0;
				line-height: inherit;
				border-style: dashed;
				height: auto;
				.icon {
					padding: 0 5px;
					color: inherit;
					font-size: inherit;
				}
				.label {
					margin: 0 10px 0 0;
					color: inherit;
					font-weight: 500;
					font-size: inherit;
				}
				.text {
					color: inherit;
					flex: 1;
					font-size: inherit;
					text-align: left;
				}
			}
			.info-item2 {
				padding: 5px 0;
				display: flex;
				width: 100%;
				border-color: #ddd;
				border-width: 0 0 1px 0;
				line-height: inherit;
				border-style: dashed;
				height: auto;
				.icon {
					padding: 0 5px;
					color: inherit;
					font-size: inherit;
				}
				.label {
					margin: 0 10px 0 0;
					color: inherit;
					font-weight: 500;
					font-size: inherit;
				}
				.text {
					color: inherit;
					flex: 1;
					font-size: inherit;
					text-align: left;
				}
			}
			.info-item3 {
				padding: 5px 0;
				display: flex;
				width: 100%;
				border-color: #ddd;
				border-width: 0 0 1px 0;
				line-height: inherit;
				border-style: dashed;
				height: auto;
				.icon {
					padding: 0 5px;
					color: inherit;
					font-size: inherit;
				}
				.label {
					margin: 0 10px 0 0;
					color: inherit;
					font-weight: 500;
					font-size: inherit;
				}
				.text {
					color: inherit;
					flex: 1;
					font-size: inherit;
					text-align: left;
				}
			}
			.info-item4 {
				padding: 5px 0;
				display: flex;
				width: 100%;
				border-color: #ddd;
				border-width: 0 0 1px 0;
				line-height: inherit;
				border-style: dashed;
				height: auto;
				.icon {
					padding: 0 5px;
					color: inherit;
					font-size: inherit;
				}
				.label {
					margin: 0 10px 0 0;
					color: inherit;
					font-weight: 500;
					font-size: inherit;
				}
				.text {
					color: inherit;
					flex: 1;
					font-size: inherit;
					text-align: left;
				}
			}
			.info-item5 {
				padding: 5px 0;
				display: flex;
				width: 100%;
				border-color: #ddd;
				border-width: 0 0 1px 0;
				line-height: inherit;
				border-style: dashed;
				height: auto;
				.icon {
					padding: 0 5px;
					color: inherit;
					font-size: inherit;
				}
				.label {
					margin: 0 10px 0 0;
					color: inherit;
					font-weight: 500;
					font-size: inherit;
				}
				.text {
					color: inherit;
					flex: 1;
					font-size: inherit;
					text-align: left;
				}
			}
			.info-item6 {
				padding: 5px 0;
				display: flex;
				width: 100%;
				border-color: #ddd;
				border-width: 0 0 1px 0;
				line-height: inherit;
				border-style: dashed;
				height: auto;
				.icon {
					padding: 0 5px;
					color: inherit;
					font-size: inherit;
				}
				.label {
					margin: 0 10px 0 0;
					color: inherit;
					font-weight: 500;
					font-size: inherit;
				}
				.text {
					color: inherit;
					flex: 1;
					font-size: inherit;
					text-align: left;
				}
			}
		}
		.center-tabs.el-tabs {
			margin: 0px auto;
			background: none;
			flex: 1;
			width: 100%;
			order: 1;
			::v-deep .el-tabs__header {
				padding: 10px 10px 0;
				margin: 0 0 20px;
				background: none;
				width: 100%;
				clear: both;
				border-color: #d10602;
				border-width: 0px;
				position: relative;
				border-style: inset;
				text-align: center;
			}
			::v-deep .el-tabs__header .el-tabs__item {
				padding: 0 20px 0 26px;
				margin: 0 10px 10px 0;
				color: #333;
				font-weight: 500;
				display: block;
				font-size: inherit;
				line-height: 44px;
				float: left;
				border-radius: 0px;
				background: #eee;
				position: relative;
				text-align: center;
				height: 44px;
			}
			::v-deep .el-tabs__header .el-tabs__item:hover {
				padding: 0 20px 0 26px;
				color: #fff;
				background: #0063CD;
				font-weight: 500;
				font-size: inherit;
				line-height: 44px;
				position: relative;
				text-align: center;
				height: 44px;
			}
			::v-deep .el-tabs__header .el-tabs__item.is-active {
				padding: 0 20px 0 26px;
				margin: 0 10px 10px 0;
				color: #fff;
				font-weight: 500;
				display: block;
				font-size: inherit;
				line-height: 44px;
				float: left;
				border-radius: 0px;
				background: #0063CD;
				position: relative;
				text-align: center;
				height: 44px;
			}
			::v-deep .el-tabs__content {
				border: 1px solid #ddd;
				padding: 20px;
				background: none;
				width: 100%;
				clear: both;
			}
			::v-deep .el-tabs__content .el-tab-pane {
				background: none;
				width: 100%;
			}
			& ::v-deep .el-tabs__header {
				.el-tabs__nav{
					overflow: auto;
				}
				::-webkit-scrollbar {
					-webkit-appearance: none;
					width: 6px;
					height: 6px;
				}
				::-webkit-scrollbar-track {
					background: rgba(0, 0, 0, 0.1);
					border-radius: 0;
				}
				::-webkit-scrollbar-thumb {
					cursor: pointer;
					border-radius: 5px;
					background: rgba(0, 0, 0, 0.15);
					transition: color 0.2s ease;
				}
				::-webkit-scrollbar-thumb:hover {
					background: rgba(0, 0, 0, 0.3);
				}
				.el-tabs__nav-wrap {
					margin: 0;
					&::after {
						content: none;
					}
				}
				.el-tabs__active-bar {
					display: none !important;
				}
			}
			.center-preview-pv {
				.center-item.el-form-item {
					border-radius: 0px;
					padding: 6px 0 0;
					margin: 0 0 20px 0;
					background: #fff;
					border-color: #ddd;
					border-width: 0px;
					border-style: solid;
					::v-deep .el-form-item__label {
						padding: 0 10px 0 0;
						color: inherit;
						font-weight: 500;
						width: 180px;
						font-size: inherit;
						line-height: 40px;
						text-align: right;
					}
					.el-form-item__content {
						margin-left: 180px;
					}
					.el-input {
						width: 100%;
					}
					.el-input ::v-deep .el-input__inner {
						border: 1px solid #ddd;
						border-radius: 0px;
						padding: 0 12px;
						box-shadow: none;
						color: inherit;
						width: auto;
						font-size: 16px;
						height: 40px;
					}
					.el-input ::v-deep .el-input__inner[readonly="readonly"] {
						border: 0;
						cursor: not-allowed;
						border-radius: 0px;
						padding: 0 12px;
						box-shadow: none;
						color: inherit;
						background: none;
						width: auto;
						font-size: 16px;
						height: 40px;
					}
					.el-select {
						width: auto;
					}
					.el-select ::v-deep .el-input__inner {
						border: 1px solid #ddd;
						border-radius: 0px;
						padding: 0 10px;
						box-shadow: none;
						color: inherit;
						width: auto;
						font-size: 16px;
						height: 40px;
					}
					.el-select ::v-deep .is-disabled .el-input__inner {
						border: 0;
						cursor: not-allowed;
						border-radius: 0px;
						padding: 0 10px;
						box-shadow: none;
						color: inherit;
						background: none;
						width: auto;
						font-size: 16px;
						height: 40px;
					}
					.el-date-editor {
						width: 100%;
					}
					.el-date-editor ::v-deep .el-input__inner {
						border: 1px solid #ddd;
						border-radius: 0px;
						padding: 0 10px 0 30px;
						box-shadow: none;
						color: inherit;
						width: auto;
						font-size: 16px;
						height: 40px;
					}
					.el-date-editor ::v-deep .el-input__inner[readonly="readonly"] {
						border: 0;
						cursor: not-allowed;
						border-radius: 0px;
						padding: 0 10px 0 30px;
						box-shadow: none;
						color: inherit;
						background: none;
						width: auto;
						font-size: 16px;
						height: 40px;
					}
					::v-deep .el-upload--picture-card {
						background: transparent;
						border: 0;
						border-radius: 0;
						width: auto;
						height: auto;
						line-height: initial;
						vertical-align: middle;
					}
					::v-deep .upload .upload-img {
						border: 1px solid #ddd;
						cursor: pointer;
						border-radius: 0px;
						color: #999;
						background: #fff;
						width: 80px;
						font-size: 32px;
						line-height: 80px;
						text-align: center;
						height: 80px;
					}
					::v-deep .el-upload-list .el-upload-list__item {
						border: 1px solid #ddd;
						cursor: pointer;
						border-radius: 0px;
						color: #999;
						background: #fff;
						width: 80px;
						font-size: 32px;
						line-height: 80px;
						text-align: center;
						height: 80px;
						font-size: 14px;
						line-height: 1.8;
					}
					::v-deep .el-upload .el-icon-plus {
						border: 1px solid #ddd;
						cursor: pointer;
						border-radius: 0px;
						color: #999;
						background: #fff;
						width: 80px;
						font-size: 32px;
						line-height: 80px;
						text-align: center;
						height: 80px;
					}
					::v-deep .el-upload__tip {
						color: #838fa1;
						font-size: 16px;
					}
					::v-deep .el-input__inner::placeholder {
						color: #999;
						font-size: inherit;
					}
				}
				.center-btn-item {
					padding: 0;
					margin: 20px 0;
					.updateBtn {
						border: 0;
						cursor: pointer;
						border-radius: 4px;
						padding: 0 24px 0 30px;
						margin: 0 20px 0 0;
						background: #0066D4;
						display: inline-block;
						width: auto;
						font-size: 16px;
						line-height: 44px;
						min-width: 120px;
						height: 44px;
						.icon {
							color: #fff;
						}
						.text {
							color: #fff;
						}
					}
					.updateBtn:hover {
						.icon {
						}
						.text {
						}
					}
					.closeBtn {
						border: 1px solid #0066D450;
						cursor: pointer;
						padding: 0 24px 0 30px;
						margin: 0 20px 0 0;
						color: #0066D4;
						display: inline-block;
						font-size: 16px;
						line-height: 44px;
						border-radius: 4px;
						background: #fff;
						width: auto;
						text-align: center;
						min-width: 110px;
						height: 44px;
						.icon {
							color: #0066D4;
						}
						.text {
							color: #0066D4;
						}
					}
					.closeBtn:hover {
						.icon {
						}
						.text {
						}
					}
				}
				.el-date-editor.el-input {
					width: auto;
				}
			}
		}
	}
	.z-box {
		padding: 0px;
		margin: 0 auto;
		display: flex;
		width: 100%;
		justify-content: space-between;
		flex-wrap: wrap;
		.section-content {
			cursor: pointer;
			padding: 20px;
			margin: 0 0 20px;
			color: #333;
			display: flex;
			border-color: #ddd;
			box-shadow: none;
			background: #fff;
			width: calc(50% - 10px);
			justify-content: space-between;
			border-width: 1px;
			align-items: center;
			position: relative;
			border-style: solid;
			.chat-left {
				display: flex;
				align-items: center;
				img {
					border-radius: 50%;
					width: 60px;
					transition: all .4s;
					height: 60px;
				}
				.chat-info {
					margin: 0 0 0 10px;
					flex-direction: column;
					display: flex;
					align-items: flex-start;
					.chat-name {
						color: #888;
						flex: 1;
						font-weight: bold;
						font-size: 14px;
						transition: all .4s;
					}
					.chat-text {
						color: #888;
						flex: 1;
						display: flex;
						font-size: 12px;
						line-height: 1.5;
						align-items: center;
						transition: all .4s;
						.chat-noread {
							border-radius: 50%;
							padding: 0 5px;
							margin: 0 2px 0 0;
							color: #fff;
							background: #f00;
							width: auto;
							font-size: 12px;
							line-height: 16px;
							text-align: center;
							height: 16px;
						}
					}
				}
			}
			.chat-btn {
				flex-direction: column;
				display: flex;
				.friendBtn1 {
					padding: 0px 10px;
					margin: 0px;
					color: #fff;
					background: rgba(75, 223, 201,1);
					text-decoration: none;
					width: 80px;
					font-size: 12px;
					line-height: 30px;
					text-align: center;
					height: 30px;
				}
				.friendBtn1:hover {
					background: rgba(75, 223, 201,.5);
				}
				.friendBtn2 {
					padding: 0px 10px;
					margin: 0px;
					color: #fff;
					background: rgba(255, 85, 0, 1.0);
					text-decoration: none;
					width: 80px;
					font-size: 12px;
					line-height: 30px;
					text-align: center;
					height: 30px;
				}
				.friendBtn2:hover {
					background: rgba(255, 85, 0, 0.5);
				}
			}
		}
		.section-content:hover {
			color: #fff;
			background: #f7db6110;
			.chat-left {
				img {
					border-radius: 5px;
				}
				.chat-info {
					.chat-name {
						color: #000;
					}
					.chat-text {
						color: #000;
						font-size: 13px;
					}
				}
			}
		}
	}
	.chat-content {
		padding-bottom: 20px;
		width: 100%;
		margin-bottom: 10px;
		max-height: 300px;
		height: 300px;
		overflow-y: scroll;
		border: 1px solid #eeeeee;
		background: #fff;
	
		.addtime {
			width: 100%;
			text-align: center;
			font-size: 12px;
		}
	
		.left-content {
			float: left;
			margin-bottom: 10px;
			padding: 10px;
			max-width: 80%;
			display: flex;
			align-items: center;
		}
	
		.right-content {
			float: right;
			margin-bottom: 10px;
			padding: 10px;
			max-width: 80%;
			display: flex;
			align-items: center;
		}
	}
	
	.clear-float {
		clear: both;
	}
	.noList {
		color: #9e9e9e;
		width: 100%;
		text-align: center;
		padding: 60px 0;
	}

	/* Personal center: replace the generated light theme with the shared obsidian product UI. */
	.center-preview.center-preview {
		box-sizing: border-box;
		display: block;
		width: min(1200px, calc(100% - 32px));
		margin: 24px auto 48px;
		padding: 0;
		color: var(--oc-text);
		background: transparent;
		font-size: 15px;

		.center-title,
		.center-info {
			display: none !important;
		}

		.center-tabs.el-tabs {
			box-sizing: border-box;
			width: 100%;
			margin: 0;
			overflow: hidden;
			border: 1px solid var(--oc-border);
			border-radius: var(--oc-radius-md);
			background: var(--oc-surface-1);
		}

		::v-deep .center-tabs > .el-tabs__header {
			box-sizing: border-box;
			width: 100%;
			margin: 0;
			padding: 14px 16px;
			border: 0;
			border-bottom: 1px solid var(--oc-border);
			background: var(--oc-surface-1);
			text-align: left;
		}

		::v-deep .center-tabs > .el-tabs__header .el-tabs__nav-wrap::after,
		::v-deep .center-tabs > .el-tabs__header .el-tabs__active-bar {
			display: none !important;
			content: none !important;
		}

		::v-deep .center-tabs > .el-tabs__header .el-tabs__nav {
			display: flex;
			gap: 8px;
			float: none;
			white-space: nowrap;
		}

		::v-deep .center-tabs > .el-tabs__header .el-tabs__item {
			box-sizing: border-box;
			display: inline-flex;
			align-items: center;
			justify-content: center;
			height: 42px;
			margin: 0;
			padding: 0 16px !important;
			border: 1px solid var(--oc-border);
			border-radius: var(--oc-radius-sm);
			background: var(--oc-surface-2);
			color: var(--oc-text-muted);
			font-weight: 600;
			line-height: 42px;
			transition: border-color .2s ease, background-color .2s ease, color .2s ease;
		}

		::v-deep .center-tabs > .el-tabs__header .el-tabs__item:hover {
			border-color: rgba(200, 111, 138, .72);
			background: rgba(200, 111, 138, .12);
			color: var(--oc-text);
		}

		::v-deep .center-tabs > .el-tabs__header .el-tabs__item.is-active {
			border-color: rgba(200, 111, 138, .86);
			background: rgba(200, 111, 138, .2);
			color: var(--oc-text);
		}

		::v-deep .center-tabs > .el-tabs__content {
			box-sizing: border-box;
			width: 100%;
			padding: 28px;
			border: 0;
			background: var(--oc-surface-0);
		}

		::v-deep .center-tabs > .el-tabs__content .el-tab-pane {
			width: 100%;
			background: transparent;
		}

		.center-preview-pv.oc-form-section {
			box-sizing: border-box;
			width: min(780px, 100%);
			margin: 0 auto;
			padding: 0;
			background: transparent;
		}

		.center-module-pane {
			box-sizing: border-box;
			width: 100%;
			min-width: 0;
		}

		.center-module-pane ::v-deep .center-embedded-module,
		.center-module-pane ::v-deep .center-embedded-module .list-preview,
		.center-module-pane ::v-deep .center-embedded-module .list {
			box-sizing: border-box !important;
			width: 100% !important;
			max-width: 100% !important;
			margin-left: 0 !important;
			margin-right: 0 !important;
		}

		.center-module-pane ::v-deep .center-embedded-module .list-preview {
			padding-top: 0 !important;
		}

		.center-module-pane ::v-deep .center-embedded-module .list-form-pv {
			margin-top: 0 !important;
		}

		.center-preview-pv .center-item.el-form-item {
			box-sizing: border-box;
			display: grid;
			grid-template-columns: 120px minmax(0, 1fr);
			align-items: center;
			width: 100%;
			margin: 0;
			padding: 0 0 18px;
			border: 0;
			background: transparent !important;
		}

		.center-preview-pv .center-item.el-form-item::before,
		.center-preview-pv .center-item.el-form-item::after {
			display: none;
			content: none;
		}

		.center-preview-pv .center-item.el-form-item ::v-deep .el-form-item__label {
			box-sizing: border-box;
			width: auto !important;
			padding: 0 16px 0 0;
			background: transparent !important;
			color: var(--oc-text-muted) !important;
			font-size: 15px;
			line-height: 50px;
			text-align: right;
		}

		.center-preview-pv .center-item.el-form-item ::v-deep .el-form-item__content {
			min-width: 0;
			margin-left: 0 !important;
			background: transparent !important;
			line-height: normal;
		}

		.center-preview-pv .center-item.el-form-item .el-input,
		.center-preview-pv .center-item.el-form-item .el-select,
		.center-preview-pv .center-item.el-form-item .el-date-editor {
			width: 100% !important;
			margin: 0;
		}

		.center-preview-pv .center-item.el-form-item ::v-deep .el-input__inner {
			box-sizing: border-box;
			width: 100% !important;
			height: 50px;
			padding: 0 16px;
			border: 1px solid var(--oc-border) !important;
			border-radius: var(--oc-radius-sm) !important;
			background: var(--oc-surface-2) !important;
			box-shadow: none !important;
			color: var(--oc-text) !important;
			font-size: 15px;
			line-height: 50px;
		}

		.center-preview-pv .center-item.el-form-item ::v-deep .el-input__inner[readonly="readonly"] {
			border: 1px solid var(--oc-border) !important;
			background: rgba(255, 255, 255, .025) !important;
			color: var(--oc-text-muted) !important;
			cursor: not-allowed;
		}

		.center-preview-pv .center-item.el-form-item ::v-deep .el-input__inner::placeholder {
			color: var(--oc-text-muted);
			opacity: .82;
		}

		.center-preview-pv .center-item.el-form-item ::v-deep .el-form-item__error {
			position: static;
			padding-top: 7px;
			color: #ef8797;
		}

		.center-preview-pv .center-item.el-form-item ::v-deep .el-upload--picture-card,
		.center-preview-pv .center-item.el-form-item ::v-deep .el-upload-list--picture-card .el-upload-list__item,
		.center-preview-pv .center-item.el-form-item ::v-deep .upload .upload-img,
		.center-preview-pv .center-item.el-form-item ::v-deep .el-upload .el-icon-plus {
			box-sizing: border-box;
			width: 96px;
			height: 96px;
			border: 1px solid var(--oc-border) !important;
			border-radius: var(--oc-radius-sm) !important;
			background: var(--oc-surface-2) !important;
			color: var(--oc-text-muted) !important;
			line-height: 94px;
		}

		.center-preview-pv .center-item.el-form-item ::v-deep .el-upload__tip {
			margin-top: 8px;
			color: var(--oc-text-muted);
			font-size: 14px;
		}

		.center-preview-pv .center-btn-item.el-form-item {
			margin: 4px 0 0;
			padding: 18px 0 0 120px;
			border-top: 1px solid var(--oc-border);
			background: transparent !important;
		}

		.center-preview-pv .center-btn-item ::v-deep .el-form-item__content {
			display: flex;
			align-items: center;
			gap: 10px;
			flex-wrap: wrap;
			margin-left: 0 !important;
		}

		.center-preview-pv .center-btn-item .el-button.updateBtn,
		.center-preview-pv .center-btn-item .el-button.closeBtn {
			box-sizing: border-box !important;
			display: inline-grid !important;
			width: 140px !important;
			min-width: 140px !important;
			height: 50px !important;
			min-height: 50px !important;
			margin: 0 !important;
			padding: 0 !important;
			border: 0 !important;
			border-radius: 0 !important;
			background: transparent !important;
			line-height: 1 !important;
		}

		.z-box {
			gap: 14px;
			padding: 0;
		}

		.z-box .section-content {
			box-sizing: border-box;
			width: calc(50% - 7px);
			margin: 0;
			padding: 16px;
			border: 1px solid var(--oc-border);
			border-radius: var(--oc-radius-sm);
			background: var(--oc-surface-1);
			color: var(--oc-text);
		}

		.z-box .section-content:hover {
			border-color: rgba(200, 111, 138, .72);
			background: rgba(200, 111, 138, .08);
		}

		.z-box .section-content .chat-left .chat-info .chat-name,
		.z-box .section-content .chat-left .chat-info .chat-text,
		.z-box .section-content:hover .chat-left .chat-info .chat-name,
		.z-box .section-content:hover .chat-left .chat-info .chat-text {
			color: var(--oc-text-muted);
		}

		.z-box .section-content .chat-btn .friendBtn2 {
			box-sizing: border-box;
			width: auto;
			height: 34px;
			padding: 0 14px;
			border: 1px solid rgba(214, 75, 88, .62);
			border-radius: var(--oc-radius-sm);
			background: rgba(214, 75, 88, .12);
			color: #f19aa5;
			line-height: 32px;
		}

		.chat-content {
			border: 1px solid var(--oc-border);
			border-radius: var(--oc-radius-sm);
			background: var(--oc-surface-0);
			color: var(--oc-text);
		}
	}

	@media (max-width: 760px) {
		.center-preview.center-preview {
			width: calc(100% - 20px);
			margin: 12px auto 32px;

			::v-deep .center-tabs > .el-tabs__header {
				padding: 10px;
				overflow-x: auto;
			}

			::v-deep .center-tabs > .el-tabs__content {
				padding: 18px 14px;
			}

			.center-preview-pv .center-item.el-form-item {
				grid-template-columns: minmax(0, 1fr);
				gap: 7px;
			}

			.center-preview-pv .center-item.el-form-item ::v-deep .el-form-item__label {
				width: 100% !important;
				padding: 0;
				line-height: 24px;
				text-align: left !important;
			}

			.center-preview-pv .center-btn-item.el-form-item {
				padding-left: 0;
			}

			.z-box .section-content {
				width: 100%;
			}
		}
	}
</style>
