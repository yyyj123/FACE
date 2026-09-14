<template>
	<div>
	<!--  -->
		<div class="breadcrumb-preview">
			<el-breadcrumb :separator="'/'">
				<el-breadcrumb-item class="item1" to="/"><a>首页</a></el-breadcrumb-item>
				<el-breadcrumb-item class="item2" v-for="(item, index) in breadcrumbItem" :key="index" :to="'/index/shouhoufuwu?centerType=' + (centerType?'1':'0')"><a>{{item.name}}</a></el-breadcrumb-item>
				<el-breadcrumb-item class="item3"><a href="javascript:void(0);">详情</a></el-breadcrumb-item>
			</el-breadcrumb>
		</div>
		<div class="back_box">
			<el-button class="backBtn" size="mini" @click="backClick">
				<span class="icon iconfont icon-jiantou33"></span>
				<span class="text">返回</span>
			</el-button>
		</div>
		<div class="detail-preview">
			<div class="attr">
				<div class="info">
					<div class="title-item">
						<div class="detail-title">
							{{detail.fuwumingcheng}}
						</div>
						<div class="colectBtn" @click="storeup(1)" v-show="!isStoreup">
							<i class="icon iconfont icon-shoucang10"></i>
							<span class="text">收藏({{detail.storeupnum}})</span>
						</div>
						<div class="colectBtnActive" @click="storeup(-1)" v-show="isStoreup">
							<i class="icon iconfont icon-shoucang12"></i>
							<span class="text">取消已收藏({{detail.storeupnum}})</span>
						</div>
					</div>
					<div class="item">
						<div class="lable">项目分类</div>
						<div class="text "  >{{detail.fuwufenlei}}</div>
					</div>
					<div class="item">
						<div class="lable">价格</div>
						<div class="text "  >{{detail.jiage}}</div>
					</div>
					<div class="item">
						<div class="lable">上架时间</div>
						<div class="text "  >{{detail.fabushijian}}</div>
					</div>
					<div class="item">
						<div class="lable">美容师账号</div>
						<div class="text "  >{{detail.weixiuzhanghao}}</div>
					</div>
					<div class="item">
						<div class="lable">美容师姓名</div>
						<div class="text "  >{{detail.weixiuxingming}}</div>
					</div>
					<div class="item">
						<div class="lable">点击次数</div>
						<div class="text "  >{{detail.clicknum}}</div>
					</div>
					<div class="btn_box">
						<el-button class="editBtn" v-if="btnAuth('shouhoufuwu','修改')" @click="editClick">修改</el-button>
						<el-button class="delBtn" v-if="btnAuth('shouhoufuwu','删除')" @click="delClick">删除</el-button>
						<el-button class="chatBtn" v-if="btnAuth('shouhoufuwu','私聊')&&detail.id!=mid" @click="chatClick">联系TA</el-button>
						<el-button class="reserveBtn" 
							v-if="btnAuth('shouhoufuwu','项目预约')" 
							@click="onAcross('fuwuyuyue','','','','')" type="warning">
							项目预约
						</el-button>
					</div>
				</div>
			</div>
		
			<el-carousel v-if="detailBanner.length" trigger="click" indicator-position="inside" arrow="always" type="default" direction="horizontal" height="480px" :autoplay="false" :interval="3000" :loop="true">
					<el-carousel-item v-for="item in detailBanner" :key="item">
						<el-image :src="resolveDetailImage(item)" :preview-src-list="[resolveDetailImage(item)]" fit="contain" class="image" style="width:100%;height:100%" alt="美容项目详情大图"></el-image>
					</el-carousel-item>
			</el-carousel>


		

		
			<el-tabs class="detail-tabs" v-model="activeName" type="border-card">
				<el-tab-pane label="项目详情" name="first">
					<div v-html="detail.fuwuxiangqing"></div>
				</el-tab-pane>
			</el-tabs>

			<div class="idea1"></div>
			<div class="idea2"></div>
		</div>
		<div class="share_view">
		</div>
		<el-dialog :visible.sync="chatVisible" @close="clearChat" :title="fname">
			<div class="chat-content" id="chat-content">
				<div v-bind:key="item.id" v-for="item in chatList">
					<div v-if="item.addtime" class="addtime">{{timeFormat(item.addtime)}}</div>
					<div v-if="item.uid==mid" class="right-content">
						<el-alert v-if="item.format==1" class="text-content" :title="item.content" :closable="false"
							type="warning"></el-alert>
						<el-image v-else fit="cover" :src="item.content?baseUrl + item.content:''" style="width: 100px;height: 100px;" :preview-src-list="[item.content?baseUrl + item.content:'']"></el-image>
						<img :src="mpic?baseUrl + mpic:require('@/assets/avator.png')" alt="" style="width: 30px;border-radius: 50%;height: 30px;margin: 0 0 0 10px;" />
					</div>
					<div v-else class="left-content">
						<img :src="fpic?baseUrl + fpic:require('@/assets/avator.png')" alt="" style="width: 30px;border-radius: 50%;height: 30px;margin: 0 10px 0 0;" />
						<el-alert v-if="item.format==1" class="text-content" :title="item.content" :closable="false"
							type="success"></el-alert>
						<el-image v-else fit="cover" :src="item.content?baseUrl + item.content:''" style="width: 100px;height: 100px;" :preview-src-list="[item.content?baseUrl + item.content:'']"></el-image>
						
					</div>
					<div class="clear-float"></div>
				</div>
			</div>
			<div slot="footer" class="dialog-footer">
				<el-input size="small" @keydown.enter.native="addChat(null)" v-model="chatForm.content" placeholder="请输入内容" style="width: calc(100% - 240px);float: left;">
				</el-input>
				<el-button size="small" :disabled="chatForm.content?false:true" type="primary" @click="addChat(null)">发送</el-button>
				<el-upload style="display: inline-block;margin: 0 6px;" class="upload-demo" :action="uploadUrl" :on-success="uploadSuccess"
					:show-file-list="false">
					<el-button size="small" type="success">上传图片</el-button>
				</el-upload>
				<el-button v-if="showSaveType" size="small" type="primary" @click="saveGood">详情图发送</el-button>
			</div>
		</el-dialog>
	</div>
</template>

<script>
	import axios from 'axios'
	import Swiper from "swiper";
	import timeMethod from '@/common/timeMethod'
	import {
		WebsocketMixin
	} from '@/mixins/WebsocketMixin'
	export default {
		mixins: [WebsocketMixin],
		//数据集合
		data() {
			return {
				tablename: 'shouhoufuwu',
				baseUrl: '',
				breadcrumbItem: [
					{
						name: '美容项目'
					}
				],
				title: '',
				detailBanner: [],
				userid: localStorage.getItem('frontUserid'),
				id: 0,
				detail: {},
				activeName: 'first',
				total: 1,
				pageSize: 5,
				totalPage: 1,
				storeupParams: {
					name: '',
					picture: '',
					refid: 0,
					tablename: 'shouhoufuwu',
					userid: localStorage.getItem('frontUserid')
				},
				isStoreup: false,
				storeupInfo: {},
				buynumber: 1,
				centerType: false,
				shareUrl: location.href,
				mid: localStorage.getItem('frontUserid'),
				chatVisible: false,
				chatList: [],
				chatForm: {
					content: ''
				},
				uploadUrl: this.$config.baseUrl + 'file/upload',
				fpic: '',
				fname: '',
				fid: '',
				mpic: localStorage.getItem('frontHeadportrait'),
				showSaveType: true,
			}
		},
		created() {
			if(this.$route.query.centerType&&this.$route.query.centerType!=0) {
				this.centerType = true
			}
			
			this.init();
		},
		mounted() {
			},
			//方法集合
			methods: {
				serviceImage(item) {
					if (item.fengmian) {
						const image = item.fengmian.split(',')[0]
						return image.startsWith('http') || image.startsWith('/') ? image : this.baseUrl + image
					}
					const images = [
						require('@/assets/images/salon/card-hydration.webp'),
						require('@/assets/images/salon/card-facial.webp'),
						require('@/assets/images/salon/card-body.webp')
					]
					return images[Math.abs(Number(item.id) || 0) % images.length]
				},
				resolveDetailImage(image) {
					if (!image) return ''
					return /^(https?:|data:|blob:|\/)/.test(image) ? image : this.baseUrl + image
				},
				init() {
					this.id = this.$route.query.id
					this.baseUrl = this.$config.baseUrl;
					this.$http.get('api/v1/services/' + this.id, {}).then(res => {
						if (res.data.code == 0) {
							const service = res.data.data || {}
							this.detail = {
								id: service.id,
								fuwumingcheng: service.name,
								fuwufenlei: service.categoryName,
								jiage: service.listPrice,
								fabushijian: '',
								weixiuzhanghao: '',
								weixiuxingming: '',
								clicknum: Number(service.clicknum) || 0,
								storeupnum: Number(service.storeupnum) || 0,
								fuwuxiangqing: service.description || service.subtitle || '',
								fengmian: service.coverUrl || '',
								durationMinutes: service.durationMinutes,
								memberPrice: service.memberPrice
							};
							this.title = this.detail.fuwumingcheng;
							this.detailBanner = [this.serviceImage(this.detail)];
							this.$forceUpdate();
							if(localStorage.getItem('frontToken')){
								this.getStoreupStatus();
							}
							this.$http.post('api/v1/services/' + this.id + '/view').then(viewRes => {
								if (viewRes.data.code == 0) this.detail.clicknum++
							})
						}
					});
				},
			async onAcross(acrossTable,crossOptAudit,crossOptPay,statusColumnName,tips,statusColumnValue,type=1){
				localStorage.setItem('crossTable',`shouhoufuwu`);
				localStorage.setItem('crossObj', JSON.stringify(this.detail));
				localStorage.setItem('statusColumnName',statusColumnName);
				localStorage.setItem('statusColumnValue',statusColumnValue);
				localStorage.setItem('tips',tips);
				if(statusColumnName!=''&&!statusColumnName.startsWith("[")) {
					var obj = JSON.parse(localStorage.getItem('crossObj'));
					for (var o in obj){
						if(o==statusColumnName && obj[o]==statusColumnValue){
							this.$message({
								type: 'error',
								message: tips,
								duration: 1500
							});
							return
						}
					}
				}
				this.$router.push({path: '/index/' + acrossTable + 'Add', query: {type: 'cross'}});
			},
				storeup(type) {
					if (type == 1 && !this.isStoreup) {
						const payload = {
							userid: Number(localStorage.getItem('frontUserid')),
							refid: Number(this.detail.id),
							tablename: 'shouhoufuwu',
							name: this.title,
							picture: this.detailBanner[0] || '',
							type: '1'
						};
						if (!payload.userid || !payload.refid || !payload.name) {
							this.$message.error('收藏信息不完整，请刷新页面后重试');
							return;
						}
						this.$http.post('storeup/add', payload).then(res => {
								if (res.data.code == 0) {
									this.isStoreup = true;
									this.detail.storeupnum++
								this.$message({
								type: 'success',
									message: '收藏成功!',
									duration: 1500,
								});
							} else {
								this.$message.error(res.data.msg || '收藏失败，请稍后重试');
							}
						});
					}
				if (type == -1 && this.isStoreup) {
					this.$http.get('storeup/list', {params: {page: 1, limit: 1, type: 1, refid: this.detail.id, tablename: 'shouhoufuwu', userid: localStorage.getItem('frontUserid')}}).then(res => {
						if (res.data.code == 0 && res.data.data.list.length > 0) {
							this.isStoreup = true;
							this.storeupInfo = res.data.data.list[0];
							let delIds = new Array();
							delIds.push(this.storeupInfo.id);
							this.$http.post('storeup/delete', delIds).then(res => {
									if (res.data.code == 0) {
										this.isStoreup = false;
										this.detail.storeupnum--
										this.$message({
										type: 'success',
										message: '取消成功!',
										duration: 1500,
									});
								}
							});
						}
					});
				}
			},
			getStoreupStatus(){
				if(localStorage.getItem("frontToken")) {
					this.$http.get('storeup/list', {params: {page: 1, limit: 1, type: 1, refid: this.detail.id, tablename: 'shouhoufuwu', userid: localStorage.getItem('frontUserid')}}).then(res => {
						if (res.data.code == 0 && res.data.data.list.length > 0) {
							this.isStoreup = true;
							this.storeupInfo = res.data.data.list[0];
						}
					});
				}
			},
			curChange(page) {
				this.getDiscussList(page);
			},
			prevClick(page) {
				this.getDiscussList(page);
			},
			nextClick(page) {
				this.getDiscussList(page);
			},
			sizeChange(size){
				this.pageSize = size
				this.getDiscussList(1);
			},
			// 返回按钮
			backClick(){
				let params = {}
				if(this.centerType){
					params.centerType = 1
				}
				this.$router.push({path: '/index/shouhoufuwu', query: params});
			},
			// 下载
			download(file ){
				if(!file) {
					this.$message({
						type: 'error',
						message: '文件不存在',
						duration: 1500,
					});
					return;
				}
				let arr = file.replace(new RegExp('upload/', "g"), "")
				axios.get(this.baseUrl + '/file/download?fileName=' + arr, {
					headers: {
						token: localStorage.getItem("frontToken")
					},
					responseType: "blob"
				}).then(({
					data
				}) => {
					const binaryData = [];
					binaryData.push(data);
					const objectUrl = window.URL.createObjectURL(new Blob(binaryData, {
						type: 'application/pdf;chartset=UTF-8'
					}))
					const a = document.createElement('a')
					a.href = objectUrl
					a.download = arr
					// a.click()
					// 下面这个写法兼容火狐
					a.dispatchEvent(new MouseEvent('click', {
						bubbles: true,
						cancelable: true,
						view: window
					}))
					window.URL.revokeObjectURL(data)
				},err=>{
					axios.get((location.href.split(this.$config.name).length>1 ? location.href.split(this.$config.name)[0] :'') + this.$config.name + '/file/download?fileName=' + arr, {
						headers: {
							token: localStorage.getItem("frontToken")
						},
						responseType: "blob"
					}).then(({
						data
					}) => {
						const binaryData = [];
						binaryData.push(data);
						const objectUrl = window.URL.createObjectURL(new Blob(binaryData, {
							type: 'application/pdf;chartset=UTF-8'
						}))
						const a = document.createElement('a')
						a.href = objectUrl
						a.download = arr
						// a.click()
						// 下面这个写法兼容火狐
						a.dispatchEvent(new MouseEvent('click', {
							bubbles: true,
							cancelable: true,
							view: window
						}))
						window.URL.revokeObjectURL(data)
					})
				})
			},


			// 权限判断
			btnAuth(tableName,key){
				if(this.centerType){
					return this.isBackAuth(tableName,key)
				}else{
					return this.isAuth(tableName,key)
				}
			},
			// 修改
			editClick(){
				this.$router.push(`/index/shouhoufuwuAdd?type=edit&&id=${this.detail.id}`);
			},
			// 删除
			async delClick(){
				await this.$confirm('是否删除此美容项目？') .then(_ => {
					this.$http.post('shouhoufuwu/delete', [this.detail.id]).then(async res => {
						if (res.data.code == 0) {
							this.$http.get('storeup/list',{params:{
								page: 1,
								limit: 100,
								refid: this.detail.id,
								tablename: 'shouhoufuwu',
							}}).then(async obj=>{
								if(obj.data&&obj.data.code==0){
									let arr = []
									for(let x in obj.data.data.list){
										arr.push(obj.data.data.list[x].id)
									}
									if(arr.length){
										await this.$http.post('storeup/delete',arr).then(()=>{})
									}
									this.$message({
										type: 'success',
										message: '删除成功!',
										duration: 1500,
										onClose: () => {
											history.back()
										}
									});
								}
							})
						}
					});
				}).catch(_ => {});
			},
		
			chatClick() {
				let user = JSON.parse(localStorage.getItem('sessionForm'))
				if(this.detail.weixiuzhanghao == user.weixiuzhanghao){
					this.$message.error('不能给自己发信息')
					return false
				}
				this.$http.get('weixiujishi/query', {
					params: {
						weixiuzhanghao: this.detail.weixiuzhanghao
					}
				}).then(res => {
					if (res.data && res.data.code == 0) {
						this.fid = res.data.data.id
						this.initWebSocket(this.fid)
						this.fname = res.data.data.weixiuzhanghao
						if (res.data.data.touxiang) {
							this.fpic = res.data.data.touxiang.split(',')[0]
						} else if (res.data.data.headportrait) {
							this.fpic = res.data.data.headportrait.split(',')[0]
						}
						this.getChatList()
						this.chatVisible = true
					}
				})
			},
			saveGood(){
				this.showSaveType = false
				this.$http.get('friend/page', {
					params: {
						uid: Number(localStorage.getItem('frontUserid')),
						fid: this.fid,
					}
				}).then(obj => {
					if (obj.data && obj.data.code == 0) {
						if (!obj.data.data.list.length) {
							this.$http.post('friend/add', {
								uid: Number(localStorage.getItem('frontUserid')),
								fid: this.fid,
								name: this.fname,
								picture: this.fpic,
								type: 2,
								tablename:  'weixiujishi' ,
							}).then(res => {
								this.$http.post('friend/add', {
									uid: this.fid,
									fid: Number(localStorage.getItem('frontUserid')),
									type: 2,
									tablename: localStorage.getItem('frontSessionTable'),
									name: localStorage.getItem('username'),
									picture: this.mpic,
								}).then(res1 => {
				
								})
							})
						}
						this.$http.post('chatmessage/add', {
							uid: Number(localStorage.getItem('frontUserid')),
							fid: this.fid,
							content: this.detailBanner[0],
							format: 2
						}).then(res2 => {
							this.chatForm = {
								content: ''
							}
							this.getChatList()
						})
					}
				})
			},
			getChatList() {
				this.$http.get('chatmessage/mlist', {
					params: {
						page: 1,
						limit: 1000,
						uid: Number(localStorage.getItem('frontUserid')),
						fid: this.fid
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
			},
			uploadSuccess(res) {
				if (res.code == 0) {
					this.addChat('upload/' + res.file)
				}
			},
			addChat(ask=null) {
				this.$http.get('friend/page', {
					params: {
						uid: Number(localStorage.getItem('frontUserid')),
						fid: this.fid,
					}
				}).then(obj => {
					if (obj.data && obj.data.code == 0) {
						if (!obj.data.data.list.length) {
							this.$http.post('friend/add', {
								uid: Number(localStorage.getItem('frontUserid')),
								fid: this.fid,
								name: this.fname,
								picture: this.fpic,
								type: 2,
								tablename:  'weixiujishi' ,
							}).then(res => {
								this.$http.post('friend/add', {
									uid: this.fid,
									fid: Number(localStorage.getItem('frontUserid')),
									type: 2,
									tablename: localStorage.getItem('frontSessionTable'),
									name: localStorage.getItem('username'),
									picture: this.mpic,
								}).then(res1 => {
			
								})
							})
						}
						this.$http.post('chatmessage/add', {
							uid: Number(localStorage.getItem('frontUserid')),
							fid: this.fid,
							content: ask?ask:this.chatForm.content,
							format: ask?2:1
						}).then(res2 => {
							this.websocketSend(ask?ask:this.chatForm.content)
							this.chatForm = {
								content: ''
							}
							this.getChatList()
						})
					}
				})
			},
		},
	}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
	.detail-preview {
		padding: 20px 0;
		margin: 0px auto;
		color: #666;
		display: flex;
		width: 1200px;
		font-size: 16px;
		justify-content: space-between;
		align-items: flex-start;
		position: relative;
		flex-wrap: wrap;
		.attr {
			padding: 0;
			background: none;
			flex: 1;
			display: block;
			width: calc(100% - 500px);
			position: relative;
			order: 2;
			.info {
				padding: 0;
				margin: 0;
				background: none;
				width: 100%;
				min-height: 560px;
				.title-item {
					border-radius: 0px;
					padding: 10px 10px;
					margin: 0 auto 3px;
					background: #0066D4;
					display: flex;
					border-color: #ddd;
					border-width: 0 0 0px;
					line-height: 1.5;
					justify-content: space-between;
					align-items: center;
					border-style: dashed;
					.detail-title {
						color: #fff;
						font-weight: 500;
						font-size: 16px;
					}
					.colectBtn {
						cursor: pointer;
						padding: 0px;
						background: none;
						.icon {
							color: #fff;
							font-size: inherit;
						}
						.text {
							color: #fff;
							font-size: inherit;
						}
					}
					.colectBtnActive {
						.icon {
							color: #ff0;
						}
						.text {
							color: #ff0;
						}
					}
					.colectBtn:hover {
						.icon {
							color: #ff0;
						}
						.text {
							color: #ff0;
						}
					}
					.colectBtnActive:hover {
						.icon {
							color: #ff0;
						}
						.text {
							color: #ff0;
						}
					}
				}
				.item {
					border-radius: 0px;
					padding: 8px 0 !important;
					margin: 0 0 3px 0;
					background: none !important;
					display: flex;
					border-color: #ddd !important;
					border-width: 1px !important;
					justify-content: spaceBetween;
					border-style: solid !important;
					.lable {
						padding: 0 10px;
						color: #333;
						white-space: nowrap;
						font-weight: 500;
						width: auto;
						font-size: inherit;
						line-height: 24px;
						text-align: right;
						height: auto;
					}
					.text {
						padding: 0px 10px 0;
						color: inherit;
						word-break: break-all;
						flex: 1;
						font-size: inherit;
						line-height: 24px;
						height: auto;
					}
					.price {
						color: #f00;
					}
					.bold {
						font-weight: bold;
					}
					.link {
						cursor: pointer;
						text-decoration: underline;
					}
				}
				.btn_box {
					padding: 10px 0;
					display: flex;
					font-size: 14px;
					flex-wrap: wrap;
				}
				.editBtn {
					border: 0px solid rgba(64, 158, 255, .3);
					cursor: pointer;
					border-radius: 4px;
					padding: 0 10px;
					margin: 0 5px 0 0;
					color: #fff;
					background: rgba(64, 158, 255, 1);
					width: auto;
					font-size: inherit;
					line-height: 40px;
					height: 40px;
				}
				.editBtn:hover {
				}
				.delBtn {
					border: 0px solid rgba(209, 6, 2, .3);
					cursor: pointer;
					border-radius: 4px;
					padding: 0 10px;
					margin: 0 5px 0 0;
					outline: none;
					color: #fff;
					background: rgba(209, 6, 2, 1);
					width: auto;
					font-size: inherit;
					line-height: 40px;
					height: 40px;
				}
				.delBtn:hover {
				}
				.chatBtn {
					border: 0px solid rgba(205, 138, 33, .3);
					cursor: pointer;
					border-radius: 4px;
					padding: 0 10px;
					margin: 0 5px 0 0;
					color: #fff;
					background: rgba(205, 138, 33, 1);
					width: auto;
					font-size: inherit;
					line-height: 40px;
					height: 40px;
				}
				.chatBtn:hover {
				}
			}
		}
		.el-carousel {
			margin: 0 40px 20px 0;
			display: block;
			width: 48%;
			text-align: center;
			height: 480px;
			order: 1;
			::v-deep .el-carousel__indicator button {
				width: 0;
				height: 0;
				display: none;
			}
			::v-deep .el-carousel__container {
				.el-carousel__arrow--left {
					width: 36px;
					font-size: 12px;
					height: 36px;
				}
				.el-carousel__arrow--left:hover {
					background: red;
				}
				.el-carousel__arrow--right {
					width: 36px;
					font-size: 12px;
					height: 36px;
				}
				.el-carousel__arrow--right:hover {
					background: red;
				}
				.el-carousel__item {
					border-radius: 0px;
					width: 100%;
					height: 100%;
					img {
						border: 1px solid #eee;
						object-fit: contain;
						width: 100%;
						height: 100%;
					}
				}
			}
		
			::v-deep .el-carousel__indicators {
				padding: 0;
				margin: 0;
				z-index: 2;
				position: absolute;
				list-style: none;
				li {
					padding: 0;
					margin: 0 4px;
					background: #fff;
					display: inline-block;
					width: 12px;
					opacity: 0.4;
					transition: 0.3s;
					height: 12px;
				}
				li:hover {
					padding: 0;
					margin: 0 4px;
					background: #fff;
					display: inline-block;
					width: 24px;
					opacity: 0.7;
					height: 12px;
				}
				li.is-active {
					padding: 0;
					margin: 0 4px;
					background: #fff;
					display: inline-block;
					width: 24px;
					opacity: 1;
					height: 12px;
				}
			}
		}
		.detail-tabs {
			border: 0px solid #ccc;
			box-shadow: none;
			padding: 0;
			margin: 20px auto 40px;
			background: #fff;
			width: 100%;
			order: 21;
			& ::v-deep .el-tabs__header .el-tabs__nav-wrap {
				margin-bottom: 0;
			}
			::v-deep .el-tabs__header {
				padding: 0;
				margin: 0;
				background: none;
				border-color: #d1060230;
				border-width: 0 0 0px 0;
				border-style: solid;
			}
			
			::v-deep .el-tabs__header .el-tabs__item {
				border: 0;
				padding: 0 20px;
				margin: 0 5px;
				color: #333;
				font-weight: 500;
				display: inline-block;
				font-size: inherit;
				line-height: 44px;
				transition: all 0s;
				background: #eee;
				position: relative;
				list-style: none;
				height: 44px;
			}
			
			::v-deep .el-tabs__header .el-tabs__item:hover {
				border: 0;
				color: #fff;
				background: #0066D4;
			}
			
			::v-deep .el-tabs__header .el-tabs__item.is-active {
				border: 0;
				margin: 0 5px;
				color: #fff;
				background: #0066D4;
				font-size: inherit;
				line-height: 44px;
				height: 44px;
			}
			
			::v-deep .el-tabs__content {
				padding: 15px;
			}
		}
	}
	.share_view{
		box-shadow: 0 1px 6px rgba(0,0,0,.3);
		z-index: 11;
		bottom: 20%;
		background: #fff;
		position: fixed;
		right: 0;
		.share:last-of-type{
			border:none;
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
	.idea1 {
		background: #fff;
		width: 100%;
		height: 1px;
		order: 4;
	}
	.idea2 {
		background: #fff;
		width: 100%;
		height: 1px;
		order: 6;
	}
</style>
