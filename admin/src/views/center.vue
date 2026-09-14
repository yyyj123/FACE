<template>
	<div :style='{"color":"#666","padding":"30px","fontSize":"16px"}'>
		<el-form
			:style='{"padding":"30px","borderColor":"#ddd","borderRadius":"12px","borderStyle":"solid","borderWidth":"1px","background":"rgba(255,255,255,0)"}'
			class="add-update-preview"
			ref="ruleForm"
			:model="ruleForm"
			label-width="180px"
			>  
				<div v-if="profileLoading" class="profile-state">正在读取个人信息…</div>
				<div v-else-if="profileError" class="profile-state profile-state--error">{{ profileError }}</div>
				<el-row v-else>
					<div v-if="flag=='account'" class="account-profile-grid">
						<el-form-item label="登录账号" prop="username">
							<el-input v-model="ruleForm.username" readonly placeholder="登录账号"></el-input>
						</el-form-item>
						<el-form-item label="账号角色" prop="role">
							<el-input :value="roleLabel" readonly placeholder="账号角色"></el-input>
						</el-form-item>
						<el-form-item label="姓名" prop="profileName">
							<el-input v-model="ruleForm.profileName" placeholder="请输入姓名" clearable></el-input>
						</el-form-item>
						<el-form-item label="联系电话" prop="profilePhone">
							<el-input v-model="ruleForm.profilePhone" placeholder="请输入联系电话" clearable></el-input>
						</el-form-item>
						<el-form-item class="account-avatar-field" label="头像" prop="touxiang">
							<file-upload
								tip="点击上传头像"
								action="file/upload"
								:limit="1"
								:multiple="false"
								:fileUrls="ruleForm.touxiang?ruleForm.touxiang:''"
								@change="accountAvatarUploadChange"
							></file-upload>
						</el-form-item>
					</div>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}'   v-if="flag=='chezhu'"  label="账号" prop="zhanghao">
					<el-input v-model="ruleForm.zhanghao" readonly						placeholder="账号" clearable></el-input>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}'   v-if="flag=='chezhu'"  label="姓名" prop="xingming">
					<el-input v-model="ruleForm.xingming" 						placeholder="姓名" clearable></el-input>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}' v-if="flag=='chezhu'"  label="性别" prop="xingbie">
					<el-select v-model="ruleForm.xingbie"  placeholder="请选择性别">
						<el-option
							v-for="(item,index) in chezhuxingbieOptions"
							v-bind:key="index"
							:label="item"
							:value="item">
						</el-option>
					</el-select>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}'   v-if="flag=='chezhu'"  label="手机" prop="shouji">
					<el-input v-model="ruleForm.shouji" 						placeholder="手机" clearable></el-input>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}' v-if="flag=='chezhu'" label="头像" prop="touxiang">
					<file-upload
						tip="点击上传头像"
						action="file/upload"
						:limit="3"
						:multiple="true"
						:fileUrls="ruleForm.touxiang?ruleForm.touxiang:''"
						@change="chezhutouxiangUploadChange"
					></file-upload>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}'   v-if="flag=='weixiujishi'"  label="美容师账号" prop="weixiuzhanghao">
					<el-input v-model="ruleForm.weixiuzhanghao" readonly						placeholder="美容师账号" clearable></el-input>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}'   v-if="flag=='weixiujishi'"  label="美容师姓名" prop="weixiuxingming">
					<el-input v-model="ruleForm.weixiuxingming" 						placeholder="美容师姓名" clearable></el-input>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}' v-if="flag=='weixiujishi'"  label="性别" prop="xingbie">
					<el-select v-model="ruleForm.xingbie"  placeholder="请选择性别">
						<el-option
							v-for="(item,index) in weixiujishixingbieOptions"
							v-bind:key="index"
							:label="item"
							:value="item">
						</el-option>
					</el-select>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}'   v-if="flag=='weixiujishi'"  label="联系电话" prop="lianxidianhua">
					<el-input v-model="ruleForm.lianxidianhua" 						placeholder="联系电话" clearable></el-input>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}' v-if="flag=='weixiujishi'" label="头像" prop="touxiang">
					<file-upload
						tip="点击上传头像"
						action="file/upload"
						:limit="3"
						:multiple="true"
						:fileUrls="ruleForm.touxiang?ruleForm.touxiang:''"
						@change="weixiujishitouxiangUploadChange"
					></file-upload>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}' v-if="flag=='users'" label="用户名" prop="username">
					<el-input v-model="ruleForm.username" placeholder="用户名"></el-input>
				</el-form-item>
				<el-form-item :style='{"border":"0px solid #eee","width":"48%","padding":"2px 20px","margin":"0 0 20px 0","display":"inline-block"}' v-if="flag=='users'" label="头像" prop="image">
					<file-upload
						tip="点击上传头像"
						action="file/upload"
						:limit="1"
						:multiple="false"
						:fileUrls="ruleForm.image?ruleForm.image:''"
						@change="usersimageUploadChange"
					></file-upload>
				</el-form-item>
					<el-form-item v-if="isSupportedProfile" class="profile-submit" :style='{"padding":"0","margin":"20px 0 0"}'>
					<el-button class="btn3" :style='{"border":"0px solid #ccc","cursor":"pointer","padding":"0 10px","margin":"0 10px 0 0","color":"#fff","borderRadius":"4px","background":"#00ad45","width":"auto","fontSize":"16px","minWidth":"110px","height":"40px"}' type="primary" @click="onUpdateHandler">
						<span class="icon iconfont icon-xihuan" :style='{"margin":"0 2px","fontSize":"14px","color":"#fff","display":"none","height":"40px"}'></span>
						提交
					</el-button>
				</el-form-item>
			</el-row>
		</el-form>
	</div>
</template>
<script>
// 校验引入
import { 
	isMobile,
} from "@/utils/validate";

export default {
	data() {
		return {
				ruleForm: {},
				flag: '',
				profileLoading: true,
				profileError: '',
				usersFlag: false,
			chezhuxingbieOptions: [],
			weixiujishixingbieOptions: [],
		};
	},
		computed: {
			isSupportedProfile() {
				return ['account', 'users', 'chezhu', 'weixiujishi'].includes(this.flag);
			},
			roleLabel() {
				const labels = {
					OWNER: '管理员',
					MANAGER: '店长',
					TECHNICIAN: '美容师',
					MEMBER: '会员'
				};
				return labels[this.ruleForm.role] || this.ruleForm.role || '未设置';
			}
		},
		mounted() {
			var table = this.$storage.get("sessionTable") || 'account';
			this.flag = table;
			this.$http({
				url: `${table}/session`,
				method: "get"
			}).then(({ data }) => {
				if (data && data.code === 0) {
					this.ruleForm = data.data;
					if (this.flag === 'account') {
						const isMember = Boolean(this.ruleForm.memberId);
						this.$set(this.ruleForm, 'profileName', isMember
							? (this.ruleForm.xingming || this.ruleForm.name || '')
							: (this.ruleForm.weixiuxingming || this.ruleForm.name || ''));
						this.$set(this.ruleForm, 'profilePhone', isMember
							? (this.ruleForm.shouji || '')
							: (this.ruleForm.lianxidianhua || ''));
					}
				} else {
					this.profileError = (data && data.msg) || '个人信息读取失败，请重新登录后重试';
				}
			}).catch(() => {
				this.profileError = '个人信息读取失败，请检查服务是否正常';
			}).finally(() => {
				this.profileLoading = false;
			});
		this.chezhuxingbieOptions = "男,女".split(',')
		this.weixiujishixingbieOptions = "男,女".split(',')
	},
		methods: {
			accountAvatarUploadChange(fileUrls) {
				this.ruleForm.touxiang = fileUrls;
			},
		chezhutouxiangUploadChange(fileUrls) {
			this.ruleForm.touxiang = fileUrls;
		},
		weixiujishitouxiangUploadChange(fileUrls) {
			this.ruleForm.touxiang = fileUrls;
		},
		usersimageUploadChange(fileUrls) {
			this.ruleForm.image = fileUrls;
		},
			onUpdateHandler() {
				if ('account' == this.flag) {
					if (!String(this.ruleForm.profileName || '').trim()) {
						this.$message.error('姓名不能为空');
						return;
					}
					if (this.ruleForm.profilePhone && !isMobile(this.ruleForm.profilePhone)) {
						this.$message.error('联系电话应输入手机格式');
						return;
					}
					if (this.ruleForm.memberId) {
						this.ruleForm.xingming = this.ruleForm.profileName;
						this.ruleForm.shouji = this.ruleForm.profilePhone;
					} else {
						this.ruleForm.weixiuxingming = this.ruleForm.profileName;
						this.ruleForm.lianxidianhua = this.ruleForm.profilePhone;
					}
				}
			if((!this.ruleForm.zhanghao)&& 'chezhu'==this.flag){
				this.$message.error('账号不能为空');
				return
			}


			if((!this.ruleForm.mima)&& 'chezhu'==this.flag){
				this.$message.error('密码不能为空');
				return
			}

			if((!this.ruleForm.xingming)&& 'chezhu'==this.flag){
				this.$message.error('姓名不能为空');
				return
			}






			if( 'chezhu' ==this.flag && this.ruleForm.shouji&&(!isMobile(this.ruleForm.shouji))){
				this.$message.error(`手机应输入手机格式`);
				return
			}


			if(this.ruleForm.touxiang!=null) {
				this.ruleForm.touxiang = this.ruleForm.touxiang.replace(new RegExp(this.$base.url,"g"),"");
			}
			if((!this.ruleForm.weixiuzhanghao)&& 'weixiujishi'==this.flag){
				this.$message.error('美容师账号不能为空');
				return
			}


			if((!this.ruleForm.mima)&& 'weixiujishi'==this.flag){
				this.$message.error('密码不能为空');
				return
			}

			if((!this.ruleForm.weixiuxingming)&& 'weixiujishi'==this.flag){
				this.$message.error('美容师姓名不能为空');
				return
			}






			if( 'weixiujishi' ==this.flag && this.ruleForm.lianxidianhua&&(!isMobile(this.ruleForm.lianxidianhua))){
				this.$message.error(`联系电话应输入手机格式`);
				return
			}


			if(this.ruleForm.touxiang!=null) {
				this.ruleForm.touxiang = this.ruleForm.touxiang.replace(new RegExp(this.$base.url,"g"),"");
			}
			if('users'==this.flag && this.ruleForm.username.trim().length<1) {
				this.$message.error(`用户名不能为空`);
				return	
			}
			if(this.flag=='users'){
				this.ruleForm.image = this.ruleForm.image.replace(new RegExp(this.$base.url,"g"),"")
			}
			this.$http({
					url: `${this.flag}/update`,
				method: "post",
				data: this.ruleForm
			}).then(({ data }) => {
				if (data && data.code === 0) {
					this.$message({
						message: "修改信息成功",
						type: "success",
						duration: 1500,
						onClose: () => {
							if(this.flag=='users'){
								this.$storage.set('headportrait',this.ruleForm.image)
							}
						}
					});
				} else {
					this.$message.error(data.msg);
				}
			});
		}
	}
};
</script>
<style lang="scss" scoped>
		.profile-state {
			display: grid;
			place-items: center;
			min-height: 220px;
			padding: 24px;
			border: 1px dashed var(--oc-border);
			border-radius: var(--oc-radius-md);
			background: var(--oc-surface-1);
			color: var(--oc-text-muted);
			text-align: center;
		}

		.profile-state--error {
			border-color: rgba(214, 75, 88, .55);
			color: #f0a4ad;
		}

		.account-profile-grid {
			display: grid;
			grid-template-columns: repeat(2, minmax(0, 1fr));
			gap: 18px 24px;
			width: 100%;
		}

		.account-profile-grid .el-form-item {
			box-sizing: border-box;
			width: 100%;
			margin: 0;
			padding: 0;
		}

		.account-profile-grid .account-avatar-field {
			grid-column: 1 / -1;
		}

		.account-profile-grid ::v-deep .el-input,
		.account-profile-grid ::v-deep .el-input__inner {
			box-sizing: border-box;
			width: 100% !important;
			max-width: 100%;
		}

		.profile-submit {
			display: flex;
			justify-content: center;
			width: 100%;
		}

		.el-date-editor.el-input {
			width: auto;
		}
	
	.add-update-preview .el-form-item ::v-deep .el-form-item__label {
				padding: 0 10px 0 0;
				color: #666;
				font-weight: 500;
				width: 180px;
				font-size: 16px;
				line-height: 40px;
				text-align: right;
			}
	
	.add-update-preview .el-form-item ::v-deep .el-form-item__content {
		margin-left: 180px;
	}
	
	.add-update-preview .el-input ::v-deep .el-input__inner {
				border: 0px solid #ccc;
				border-radius: 0px;
				padding: 0 12px;
				color: #666;
				width: auto;
				font-size: 16px;
				height: 34px;
			}
	
	.add-update-preview .el-select ::v-deep .el-input__inner {
				border: 0px solid #ccc;
				border-radius: 0px;
				padding: 0 10px;
				color: #666;
				width: auto;
				font-size: 16px;
				height: 34px;
			}
	
	.add-update-preview .el-date-editor ::v-deep .el-input__inner {
				border: 0px solid #ccc;
				border-radius: 0px;
				padding: 0 10px 0 30px;
				color: #666;
				width: auto;
				font-size: 16px;
				height: 34px;
			}
	
	.add-update-preview ::v-deep .el-upload--picture-card {
		background: transparent;
		border: 0;
		border-radius: 0;
		width: auto;
		height: auto;
		line-height: initial;
		vertical-align: middle;
	}
	
	.add-update-preview ::v-deep .el-upload-list .el-upload-list__item {
				border: 0px solid #ccc;
				cursor: pointer;
				border-radius: 0px;
				color: #666;
				background: #fff;
				object-fit: cover;
				width: 90px;
				font-size: 24px;
				line-height: 60px;
				text-align: center;
				height: 60px;
			}
	
	.add-update-preview ::v-deep .el-upload .el-icon-plus {
				border: 0px solid #ccc;
				cursor: pointer;
				border-radius: 0px;
				color: #666;
				background: #fff;
				object-fit: cover;
				width: 90px;
				font-size: 24px;
				line-height: 60px;
				text-align: center;
				height: 60px;
			}
	
	.add-update-preview .el-textarea ::v-deep .el-textarea__inner {
				border: 0px solid #ccc;
				border-radius: 0px;
				padding: 12px;
				color: #666;
				background: #fff;
				width: auto;
				font-size: 16px;
				min-width: 400px;
				height: 120px;
			}
	
	.add-update-preview .btn3 {
				border: 0px solid #ccc;
				cursor: pointer;
				border-radius: 4px;
				padding: 0 10px;
				margin: 0 10px 0 0;
				color: #fff;
				background: #00ad45;
				width: auto;
				font-size: 16px;
				min-width: 110px;
				height: 40px;
			}
	
	.add-update-preview .btn3:hover {
				opacity: 0.8;
			}
	
		.editor>.avatar-uploader {
			line-height: 0;
			height: 0;
		}

		@media (max-width: 900px) {
			.account-profile-grid {
				grid-template-columns: minmax(0, 1fr);
			}

			.account-profile-grid .account-avatar-field {
				grid-column: auto;
			}
		}
	</style>
